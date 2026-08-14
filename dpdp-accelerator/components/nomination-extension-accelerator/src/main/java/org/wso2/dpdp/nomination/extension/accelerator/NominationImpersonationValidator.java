package org.wso2.dpdp.nomination.extension.accelerator;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.exception.UserIdNotFoundException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.authz.OAuthAuthzReqMessageContext;
import org.wso2.carbon.identity.oauth2.impersonation.models.ImpersonationContext;
import org.wso2.carbon.identity.oauth2.impersonation.models.ImpersonationRequestDTO;
import org.wso2.carbon.identity.oauth2.impersonation.validators.ImpersonationValidator;

/**
 * A custom {@link ImpersonationValidator} that stands in the same validation
 * line WSO2 IS runs before minting an impersonation subject token, and does two
 * things the built-in validators cannot.
 *
 * <p><b>1. The nomination gate.</b> The built-in SubjectScopeValidator only
 * confirms the caller holds the impersonation scope - it never checks whether
 * the caller (the nominee) is actually nominated for the user being impersonated
 * (the owner). This validator adds exactly that check. It does NOT read the
 * nominee database directly; it asks nominee-service, which owns that data, so
 * IS stays decoupled from the application's schema. Fails closed: if the
 * nomination cannot be confirmed, impersonation is denied.
 *
 * <p><b>2. Scope narrowing.</b> SubjectScopeValidator computes the approved
 * scopes <i>as the owner</i> - it swaps the owner in as the request user,
 * validates, and swaps back. The result is everything the owner may do, which
 * for a nomination is far too much: the owner grants each nominee a specific
 * subset. This validator intersects the approved scopes down to that subset, so
 * the subject token (and therefore the exchanged access token, which inherits
 * the subject token's scopes as a hard ceiling) can never carry more authority
 * than the owner actually delegated.
 *
 * <p><b>Ordering matters and is guaranteed.</b> IS sorts validators by priority
 * descending ({@code Comparator.comparingInt(getPriority).reversed()}), so the
 * <i>lowest</i> number runs last. SubjectScopeValidator sits at 80 and calls
 * {@code setApprovedScope(...)}; anything written before it would simply be
 * overwritten. This validator therefore runs below it, where its narrowing is
 * the final word.
 */
public class NominationImpersonationValidator implements ImpersonationValidator {

    private static final Log LOG = LogFactory.getLog(NominationImpersonationValidator.class);

    private static final String NAME = "NominationImpersonationValidator";
    private static final String ERROR_CODE = "OPENFGC-IMP-001";

    /**
     * Scopes governed by a nomination permission. A scope in this map survives
     * only if the owner granted the corresponding permission to this nominee.
     *
     * <p>Deliberately a deny-list over nominee-specific scopes rather than an
     * allow-list over everything: scopes such as {@code openid} and
     * {@code internal_user_impersonate} are required for the flow itself and
     * must pass through untouched. Only scopes we know how to govern are
     * candidates for removal.
     *
     * <p>Must stay in sync with the delegatable scopes the BFF requests and with
     * {@code NomineePermission} in nominee-service.
     */
    private static final Map<String, String> SCOPE_TO_PERMISSION = Map.of(
            "portal:consents:read:self", "CONSENT_VIEW",
            "portal:consents:write:self", "CONSENT_REVOKE",
            "portal:consents:approve:self", "CONSENT_APPROVE",
            "portal:profile:read:self", "ACCOUNT_VIEW",
            "portal:profile:write:self", "ACCOUNT_UPDATE",
            "portal:profile:delete:self", "ACCOUNT_DELETE");

    // Overridable via -D system properties on IS startup; defaults suit local dev.
    private static final String GATE_BASE_URL =
            System.getProperty("nominee.gate.url", "http://localhost:8082");
    private static final String GATE_API_KEY =
            System.getProperty("nominee.gate.key", "dev-impersonation-gate-key");

    private static final Pattern PERMISSIONS_ARRAY =
            Pattern.compile("\"permissions\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    /** The shape of a local user id, used to tell one from a username. */
    private static final Pattern USER_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Built on first use, never at construction.
     *
     * <p>{@link HttpClient.Builder#build()} resolves {@code SSLContext.getDefault()}
     * when no context is supplied, and that call initialises the JVM-wide default
     * context permanently from the truststore configured at that instant. This
     * bundle starts before the server has configured its own truststore, so
     * building a client here would freeze a default context that trusts only the
     * JDK's bundled authorities - breaking every later TLS client in the process,
     * including the server's own, not just this one.
     *
     * <p>By the time an impersonation request arrives the server is fully started,
     * so the context resolved then is the correct one.
     */
    private volatile HttpClient http;

    /**
     * Runs last. See the class javadoc: IS sorts validators highest-priority
     * first, so a number below the built-in SubjectScopeValidator's 
     * puts this validator after it - the only position from which approved
     * scopes can be narrowed without being overwritten.
     */
    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getImpersonationValidatorName() {
        return NAME;
    }

    @Override
    public ImpersonationContext validateImpersonation(ImpersonationContext context)
            throws IdentityOAuth2Exception {

        ImpersonationRequestDTO request = context.getImpersonationRequestDTO();
        String ownerId = request.getSubject();
        String nomineeId = resolveNomineeId(request.getImpersonator());

        if (ownerId == null || nomineeId == null) {
            LOG.warn("Impersonation denied: could not resolve owner or nominee id.");
            return deny(context, "Impersonation denied: could not resolve owner or nominee id.");
        }

        NominationDecision decision;
        try {
            decision = fetchDecision(ownerId, nomineeId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Nomination gate call interrupted; denying impersonation. "
                    + "owner=" + ownerId + " nominee=" + nomineeId, e);
            return deny(context, "Impersonation denied: nomination could not be verified.");
        } catch (IOException | RuntimeException e) {
            LOG.error("Nomination gate call failed; denying impersonation. "
                    + "owner=" + ownerId + " nominee=" + nomineeId, e);
            return deny(context, "Impersonation denied: nomination could not be verified.");
        }

        if (!decision.active()) {
            LOG.warn("Impersonation denied: no active nomination. owner=" + ownerId
                    + " nominee=" + nomineeId);
            return deny(context, "Impersonation denied: no active nomination for this user.");
        }

        narrowScopes(request, decision.permissions(), ownerId, nomineeId);

        LOG.info("Impersonation allowed: active nomination confirmed. owner=" + ownerId
                + " nominee=" + nomineeId + " permissions=" + decision.permissions());
        context.setValidated(true);
        return context;
    }

    /**
     * Removes any nominee-governed scope the owner did not grant this nominee.
     *
     * <p>Runs after SubjectScopeValidator has populated the approved scopes with
     * everything the <i>owner</i> may do. Non-governed scopes are left alone.
     */
    private void narrowScopes(ImpersonationRequestDTO request, Set<String> permissions,
                              String ownerId, String nomineeId) {

        OAuthAuthzReqMessageContext authzContext = request.getoAuthAuthzReqMessageContext();
        if (authzContext == null) {
            LOG.warn("No authorization context available; cannot narrow scopes for owner="
                    + ownerId + " nominee=" + nomineeId);
            return;
        }

        String[] approved = authzContext.getApprovedScope();
        if (approved == null || approved.length == 0) {
            LOG.debug("No approved scopes to narrow for owner=" + ownerId + " nominee=" + nomineeId);
            return;
        }

        Set<String> retained = new LinkedHashSet<>();
        Set<String> dropped = new LinkedHashSet<>();
        for (String scope : approved) {
            String requiredPermission = SCOPE_TO_PERMISSION.get(scope);
            if (requiredPermission == null || permissions.contains(requiredPermission)) {
                retained.add(scope);
            } else {
                dropped.add(scope);
            }
        }

        if (!dropped.isEmpty()) {
            LOG.info("Narrowed impersonation scopes for owner=" + ownerId + " nominee=" + nomineeId
                    + " dropped=" + dropped + " retained=" + retained);
        }
        authzContext.setApprovedScope(retained.toArray(new String[0]));
    }

    /**
     * The nominee is the impersonator (the logged-in user). getUserId() can throw
     * UserIdNotFoundException when the id field is not populated on the
     * AuthenticatedUser, so fall back to the subject identifier / masked id, both
     * of which carry the same UUID for a local user.
     */
    private String resolveNomineeId(AuthenticatedUser impersonator) {
        if (impersonator == null) {
            return null;
        }
        try {
            String id = impersonator.getUserId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (UserIdNotFoundException e) {
            LOG.debug("getUserId() unavailable, trying subject identifier fallback", e);
        }
        try {
            String subjectId = impersonator.getAuthenticatedSubjectIdentifier();
            if (subjectId != null && USER_ID.matcher(subjectId).matches()) {
                return subjectId;
            }
            String masked = impersonator.getLoggableMaskedUserId();
            if (masked != null && USER_ID.matcher(masked).matches()) {
                return masked;
            }
        } catch (RuntimeException e) {
            LOG.debug("nominee id fallback failed", e);
        }
        return null;
    }

    /** What nominee-service says about one owner/nominee pairing right now. */
    private record NominationDecision(boolean active, Set<String> permissions) {
    }

    /**
     * Asks nominee-service both questions at once: is the pairing active, and
     * which permissions does it carry. One call keeps the mint path to a single
     * round trip.
     */
    private NominationDecision fetchDecision(String ownerId, String nomineeId)
            throws IOException, InterruptedException {
        String url = GATE_BASE_URL + "/internal/nominations/permissions"
                + "?owner=" + URLEncoder.encode(ownerId, StandardCharsets.UTF_8)
                + "&nominee=" + URLEncoder.encode(nomineeId, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("X-Internal-Key", GATE_API_KEY)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("nominee-service returned HTTP " + resp.statusCode());
        }

        String body = resp.body() == null ? "" : resp.body();
        boolean active = body.replaceAll("\\s+", "").contains("\"active\":true");
        return new NominationDecision(active, parsePermissions(body));
    }

    /**
     * Minimal, dependency-free parse of {@code "permissions":["A","B"]}.
     *
     * <p>A JSON library would be cleaner, but this bundle is deployed into IS's
     * OSGi runtime where every added dependency is another version conflict to
     * manage. The response shape is owned by nominee-service and is this simple
     * by agreement.
     */
    private static Set<String> parsePermissions(String body) {
        Set<String> permissions = new LinkedHashSet<>();
        Matcher matcher = PERMISSIONS_ARRAY.matcher(body);
        if (!matcher.find()) {
            return permissions;
        }
        String contents = matcher.group(1);
        if (contents == null || contents.isBlank()) {
            return permissions;
        }
        Arrays.stream(contents.split(","))
                .map(entry -> entry.replace("\"", "").trim())
                .filter(entry -> !entry.isEmpty())
                .forEach(permissions::add);
        return permissions;
    }

    private HttpClient httpClient() {
        HttpClient client = http;
        if (client == null) {
            synchronized (this) {
                client = http;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .build();
                    http = client;
                }
            }
        }
        return client;
    }

    private ImpersonationContext deny(ImpersonationContext context, String message) {
        context.setValidated(false);
        context.setValidationFailureErrorCode(ERROR_CODE);
        context.setValidationFailureErrorMessage(message);
        return context;
    }
}
