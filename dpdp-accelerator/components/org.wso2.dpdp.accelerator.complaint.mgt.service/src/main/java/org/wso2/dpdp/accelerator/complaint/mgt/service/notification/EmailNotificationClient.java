/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.dpdp.accelerator.common.util.EmailValidator;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link NotificationClient} implementation routing through WSO2 IS's native email notification
 * mechanism. Resolves every recipient (officers, or the complaint's creator) itself, then fires a
 * standard {@code TRIGGER_NOTIFICATION} event per recipient so IS's own registered notification
 * handler does the actual templated-email + SMTP dispatch - no custom event, no round trip through
 * identity.extensions. OSGi services are read live from {@link ComplaintServiceDataHolder}, kept
 * up to date by {@code ComplaintServiceComponent}'s {@code @Reference} bindings.
 */
public class EmailNotificationClient implements NotificationClient {

    private static final Log LOG = LogFactory.getLog(EmailNotificationClient.class);

    private static final String NOTIFICATION_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    private static final String NOTIFICATION_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";
    private static final String NOTIFICATION_TYPE_COMPLAINT_ACKNOWLEDGED = "ComplaintAcknowledged";

    // Property keys IS's own already-registered internal notification handler expects on a
    // standard TRIGGER_NOTIFICATION event. Conventional string keys, not typed API - mirror
    // org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.TEMPLATE_TYPE/.SEND_TO exactly,
    // hardcoded here rather than adding a dependency on that artifact just for two string literals.
    private static final String TRIGGER_PROP_SEND_TO = "send-to";
    private static final String TRIGGER_PROP_TEMPLATE_TYPE = "TEMPLATE_TYPE";
    private static final String PROP_REFERENCE_ID = "reference-id";
    private static final String PROP_MESSAGE_EXCERPT = "message-excerpt";

    // Placeholder keys the shared complaint-email-body.html template references (registered per
    // tenant by identity.extensions' EmailTemplateProvisioningUtil - template content is unaffected
    // by which module triggers the send).
    private static final String PLACEHOLDER_DATA_PRINCIPAL_NAME = "data-principal-name";
    private static final String PLACEHOLDER_ACTOR_NAME = "actor-name";
    private static final String PLACEHOLDER_CATEGORY_LABEL = "category-label";
    private static final String PLACEHOLDER_PRIORITY_LABEL = "priority-label";
    private static final String PLACEHOLDER_STATUS_LABEL = "status-label";
    private static final String PLACEHOLDER_SLA_LABEL = "sla-label";
    private static final String PLACEHOLDER_ACTION_URL = "action-url";
    private static final String PLACEHOLDER_RECIPIENT_ROLE_LABEL = "recipient-role-label";
    private static final String PLACEHOLDER_HEADLINE_HTML = "headline-html";
    private static final String PLACEHOLDER_FOOTER_TEXT = "footer-text";
    private static final String PLACEHOLDER_ACTION_BADGE_HTML = "action-badge-html";
    private static final String PLACEHOLDER_ACTION_BUTTON_HTML = "action-button-html";
    private static final String PLACEHOLDER_LOGO_URL = "logo-url";

    private static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";
    private static final String GIVEN_NAME_CLAIM = "http://wso2.org/claims/givenname";
    private static final String LAST_NAME_CLAIM = "http://wso2.org/claims/lastname";

    // Mirrors ComplaintActorRole.COMPLAINT_OFFICER.name() from the complaint.mgt.dao module - not
    // depended on directly here, since the actor role already crosses into ComplaintEvent as a
    // plain string.
    private static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";
    // Mirrors identity.extensions' DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME /
    // DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE - duplicated rather than depended on, same
    // as every other constant in this class.
    private static final String APPLICATION_NAME = "DPDP Consent Portal";
    private static final String ADMIN_ROLE = "dpdp-consent-admin";
    private static final String ROLE_AUDIENCE = "organization";

    // Mirrors the portal frontend's own status labels exactly (complaintDisplay.ts's
    // STATUS_LABEL_KEYS + public/i18n/en/common.json's complaints.status.* strings) rather than a
    // narrative computed here, so the same complaint shows the same status word in both places.
    private static final Map<String, String> STATUS_LABELS = buildStatusLabels();
    private static final String ROLE_LABEL_GRIEVANCE_OFFICER = "Grievance Officer";
    private static final String ROLE_LABEL_DATA_PRINCIPAL = "Data Principal";
    // The portal has two separate routes for the same complaint (App.tsx): "/complaints/:id" is
    // the citizen's own self-service view (COMPLAINTS_READ_SELF scope, backed by the /me/
    // complaints/{id} API), "/complaint-management/:id" is the officer's view (COMPLAINTS_READ_ANY
    // scope, backed by /complaints/{id}). Linking an officer to the citizen route 404s - so which
    // path to use depends on who this email is actually going to.
    private static final String CREATOR_DEEP_LINK_PATH = "/consent-portal/complaints/";
    private static final String OFFICER_DEEP_LINK_PATH = "/consent-portal/complaint-management/";
    // Same PNG the portal itself serves next to its "Consent Portal" brand title.
    private static final String LOGO_PATH = "/consent-portal/wso2-logo.png";
    private static final String STATUS_WAITING_ON_CLIENT = "WAITING_ON_CLIENT";
    private static final String STATUS_RESOLVED = "RESOLVED";
    // A freshly created complaint is always OPEN (see ComplaintServiceImpl#createComplaint).
    private static final String STATUS_LABEL_OPEN = "Open";
    // Same neutral "Update" styling buildActionBadge would compute for a non-actionable recipient -
    // a receipt isn't "action needed", regardless of status.
    private static final String ACKNOWLEDGEMENT_BADGE_HTML =
            "<span style=\"display:inline-block;background-color:#f3f4f6;color:#4b5563;font-size:11px;"
                    + "font-weight:700;letter-spacing:0.04em;text-transform:uppercase;padding:4px 10px;"
                    + "border-radius:999px;\">Update</span>";

    private static final int MAX_EXCERPT_LENGTH = 300;

    private final Supplier<IdentityEventService> eventServiceSupplier;
    private final Supplier<RealmService> realmServiceSupplier;
    private final Supplier<ApplicationManagementService> applicationManagementServiceSupplier;
    private final Supplier<RoleManagementService> roleManagementServiceSupplier;
    private final Supplier<OrganizationManager> organizationManagerSupplier;

    public EmailNotificationClient() {
        this(() -> ComplaintServiceDataHolder.getInstance().getIdentityEventService(),
                () -> ComplaintServiceDataHolder.getInstance().getRealmService(),
                () -> ComplaintServiceDataHolder.getInstance().getApplicationManagementService(),
                () -> ComplaintServiceDataHolder.getInstance().getRoleManagementService(),
                () -> ComplaintServiceDataHolder.getInstance().getOrganizationManager());
    }

    /** Overload for tests injecting 4 suppliers. */
    EmailNotificationClient(Supplier<IdentityEventService> eventServiceSupplier,
            Supplier<RealmService> realmServiceSupplier,
            Supplier<ApplicationManagementService> applicationManagementServiceSupplier,
            Supplier<RoleManagementService> roleManagementServiceSupplier) {
        this(eventServiceSupplier, realmServiceSupplier, applicationManagementServiceSupplier,
                roleManagementServiceSupplier, () -> null);
    }

    /** Test seam - lets a test inject mock suppliers instead of a real OSGi lookup. */
    EmailNotificationClient(Supplier<IdentityEventService> eventServiceSupplier,
            Supplier<RealmService> realmServiceSupplier,
            Supplier<ApplicationManagementService> applicationManagementServiceSupplier,
            Supplier<RoleManagementService> roleManagementServiceSupplier,
            Supplier<OrganizationManager> organizationManagerSupplier) {
        this.eventServiceSupplier = eventServiceSupplier;
        this.realmServiceSupplier = realmServiceSupplier;
        this.applicationManagementServiceSupplier = applicationManagementServiceSupplier;
        this.roleManagementServiceSupplier = roleManagementServiceSupplier;
        this.organizationManagerSupplier = organizationManagerSupplier;
    }

    @Override
    public void notifyComplaintCreated(Complaint complaint) {
        try {
            List<Recipient> officers = resolveOfficers(complaint.getOrgId());
            if (officers.isEmpty()) {
                LOG.debug("No complaint officers resolved for tenant '" + LogSanitizer.sanitize(complaint.getOrgId())
                        + "'; nothing to notify.");
            } else {
                Map<String, Object> placeholders = buildTemplatePlaceholders(complaint, null, false,
                        NOTIFICATION_TYPE_COMPLAINT_CREATED);
                for (Recipient officer : officers) {
                    triggerNotification(officer, complaint.getOrgId(), NOTIFICATION_TYPE_COMPLAINT_CREATED,
                            placeholders);
                }
            }
        } catch (Throwable t) {
            // Deliberately never rethrown - see interface javadoc. The complaint write this is
            // called after has already committed; a notification failure must not surface as one.
            LOG.error("Error notifying complaint officers", t);
        }
        sendAcknowledgement(complaint);
    }

    @Override
    public void notifyCommentAdded(Complaint complaint, ComplaintEvent event) {
        try {
            boolean notifyingCreator = ACTOR_ROLE_COMPLAINT_OFFICER.equals(event.getActorRole());
            List<Recipient> recipients = notifyingCreator
                    ? resolveCreatorRecipient(complaint).map(Collections::singletonList)
                            .orElseGet(Collections::emptyList)
                    : resolveOfficers(complaint.getOrgId());
            if (recipients.isEmpty()) {
                LOG.debug("No recipients resolved for a comment-added complaint notification in tenant '"
                        + LogSanitizer.sanitize(complaint.getOrgId()) + "'; nothing to send.");
                return;
            }
            Map<String, Object> placeholders = buildTemplatePlaceholders(complaint, event, notifyingCreator,
                    NOTIFICATION_TYPE_COMMENT_ADDED);
            for (Recipient recipient : recipients) {
                triggerNotification(recipient, complaint.getOrgId(), NOTIFICATION_TYPE_COMMENT_ADDED, placeholders);
            }
        } catch (Throwable t) {
            // Deliberately never rethrown - see interface javadoc.
            LOG.error("Error sending complaint comment notification", t);
        }
    }

    /**
     * Sends the "we've received your complaint" receipt to its one, already-known recipient - the
     * complaint's own creator.
     */
    private void sendAcknowledgement(Complaint complaint) {
        try {
            AbstractUserStoreManager userStoreManager = resolveUserStoreManager(complaint.getOrgId());
            if (userStoreManager == null) {
                LOG.debug("Could not resolve a user store manager for tenant '" + LogSanitizer.sanitize(complaint.getOrgId())
                        + "'; complaint acknowledgement not sent.");
                return;
            }
            Optional<String> username = resolveUsername(userStoreManager, complaint.getUserId(),
                    complaint.getUserName());
            if (!username.isPresent()) {
                LOG.debug("Could not resolve a username for complaint creator (userId: "
                        + LogSanitizer.sanitize(complaint.getUserId()) + "); complaint acknowledgement not sent.");
                return;
            }
            Optional<String> email = resolveEmail(userStoreManager, username.get());
            if (!email.isPresent()) {
                LOG.debug("No email claim resolvable for user '" + LogSanitizer.sanitize(username.get())
                        + "'; complaint acknowledgement not sent.");
                return;
            }
            String displayName = resolveDisplayName(userStoreManager, username.get());

            IdentityEventService eventService = eventServiceSupplier.get();
            if (eventService == null) {
                LOG.debug("IdentityEventService is not resolvable; complaint acknowledgement not sent.");
                return;
            }

            Map<String, Object> properties = new HashMap<>();
            properties.put(TRIGGER_PROP_SEND_TO, email.get());
            properties.put(IdentityEventConstants.EventProperty.USER_NAME, username.get());
            properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, complaint.getOrgId());
            properties.put(TRIGGER_PROP_TEMPLATE_TYPE, NOTIFICATION_TYPE_COMPLAINT_ACKNOWLEDGED);
            properties.put(PROP_REFERENCE_ID, complaint.getReferenceId());
            properties.put(PROP_MESSAGE_EXCERPT, excerpt(complaint.getDescription()));
            properties.put(PLACEHOLDER_DATA_PRINCIPAL_NAME, displayName);
            properties.put(PLACEHOLDER_ACTOR_NAME, displayName);
            properties.put(PLACEHOLDER_CATEGORY_LABEL, humanize(complaint.getCategory()));
            properties.put(PLACEHOLDER_PRIORITY_LABEL, humanize(complaint.getPriority()));
            properties.put(PLACEHOLDER_STATUS_LABEL, STATUS_LABEL_OPEN);
            properties.put(PLACEHOLDER_SLA_LABEL, slaLabel(complaint.getStatutoryDueTime()));
            properties.put(PLACEHOLDER_ACTION_URL,
                    IdentityUtil.getServerURL(CREATOR_DEEP_LINK_PATH + complaint.getComplaintId(), true, false));
            properties.put(PLACEHOLDER_LOGO_URL, IdentityUtil.getServerURL(LOGO_PATH, true, false));
            properties.put(PLACEHOLDER_HEADLINE_HTML,
                    "Your complaint <strong>" + htmlEscape(complaint.getReferenceId()) + "</strong> has been "
                            + "received.");
            properties.put(PLACEHOLDER_FOOTER_TEXT,
                    "You're receiving this because you filed this complaint. We'll email you when there's "
                            + "an update.");
            properties.put(PLACEHOLDER_ACTION_BADGE_HTML, ACKNOWLEDGEMENT_BADGE_HTML);
            // Deliberately empty, and deliberately still set: IS leaves an unmatched placeholder
            // in the template as literal text, so omitting the key ships "{{action-button-html}}"
            // in the mail body. See buildActionButton for why this type has no button.
            properties.put(PLACEHOLDER_ACTION_BUTTON_HTML, "");

            eventService.handleEvent(new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION, properties));
        } catch (Throwable t) {
            // Deliberately never rethrown - see interface javadoc.
            LOG.error("Error sending complaint acknowledgement", t);
        }
    }

    /**
     * Resolves every member of {@code dpdp-consent-admin} for the given tenant that has a
     * resolvable email address. Members without one are skipped (logged), not fatal to the batch.
     */
    private List<Recipient> resolveOfficers(String tenantDomain) {
        List<Recipient> recipients = new ArrayList<>();
        try {
            RoleManagementService roleManagementService = roleManagementServiceSupplier.get();
            OrganizationManager organizationManager = organizationManagerSupplier.get();
            AbstractUserStoreManager userStoreManager = resolveUserStoreManager(tenantDomain);
            if (roleManagementService == null || userStoreManager == null) {
                LOG.debug("Required OSGi services are not resolvable; cannot resolve complaint officers to "
                        + "notify for tenant: " + LogSanitizer.sanitize(tenantDomain));
                return recipients;
            }

            String organizationId = null;
            boolean resolvedAsOrganization = false;
            if (organizationManager != null) {
                organizationId = organizationManager.resolveOrganizationId(tenantDomain);
                resolvedAsOrganization = organizationId != null;
            }
            if (organizationId == null) {
                // Fallback to application lookup if organization ID cannot be resolved
                ApplicationManagementService applicationManagementService = applicationManagementServiceSupplier.get();
                if (applicationManagementService != null) {
                    ServiceProvider serviceProvider = applicationManagementService
                            .getApplicationExcludingFileBasedSPs(APPLICATION_NAME, tenantDomain);
                    if (serviceProvider != null) {
                        organizationId = serviceProvider.getApplicationResourceId();
                    }
                }
            }
            if (organizationId == null) {
                LOG.debug("Could not resolve organization or application ID for tenant '" + LogSanitizer.sanitize(tenantDomain)
                        + "'; cannot resolve complaint officers to notify.");
                return recipients;
            }

            // The audience must match whichever ID source actually produced organizationId above -
            // passing an application resource ID with audience="organization" (or vice versa)
            // throws INVALID_AUDIENCE (see DPDPConsentPortalRoleProvisioningUtil's own comment on
            // this same constraint), so this can never be derived from organizationManager's mere
            // presence, only from which branch above actually resolved the ID.
            String audience = resolvedAsOrganization ? ROLE_AUDIENCE : "application";
            if (!roleManagementService.isExistingRoleName(ADMIN_ROLE, audience, organizationId, tenantDomain)) {
                LOG.debug("Role '" + ADMIN_ROLE + "' does not exist for tenant '" + LogSanitizer.sanitize(tenantDomain)
                        + "'; cannot resolve complaint officers to notify.");
                return recipients;
            }
            String roleId = roleManagementService.getRoleIdByName(ADMIN_ROLE, audience, organizationId,
                    tenantDomain);
            List<UserBasicInfo> members = roleManagementService.getUserListOfRole(roleId, tenantDomain);
            for (UserBasicInfo member : members) {
                resolveEmail(userStoreManager, member.getName())
                        .ifPresent(email -> recipients.add(new Recipient(member.getName(), email)));
            }
        } catch (Exception e) {
            LOG.error("Error resolving complaint officers to notify for tenant: " + LogSanitizer.sanitize(tenantDomain), e);
        }
        return recipients;
    }

    /** Resolves a complaint's original creator - used to notify them when an officer comments. */
    private Optional<Recipient> resolveCreatorRecipient(Complaint complaint) {
        AbstractUserStoreManager userStoreManager = resolveUserStoreManager(complaint.getOrgId());
        if (userStoreManager == null) {
            return Optional.empty();
        }
        Optional<String> username = resolveUsername(userStoreManager, complaint.getUserId(),
                complaint.getUserName());
        if (!username.isPresent()) {
            LOG.debug("Could not resolve a username for complaint creator (userId: "
                    + LogSanitizer.sanitize(complaint.getUserId()) + "); cannot notify them.");
            return Optional.empty();
        }
        return resolveEmail(userStoreManager, username.get()).map(email -> new Recipient(username.get(), email));
    }

    private AbstractUserStoreManager resolveUserStoreManager(String tenantDomain) {
        RealmService realmService = realmServiceSupplier.get();
        if (realmService == null) {
            LOG.debug("RealmService is not resolvable for tenant: " + LogSanitizer.sanitize(tenantDomain));
            return null;
        }
        try {
            int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
            UserRealm userRealm = realmService.getTenantUserRealm(tenantId);
            if (userRealm == null) {
                return null;
            }
            UserStoreManager userStoreManager = userRealm.getUserStoreManager();
            return userStoreManager instanceof AbstractUserStoreManager
                    ? (AbstractUserStoreManager) userStoreManager : null;
        } catch (Exception e) {
            LOG.error("Error resolving user store manager for tenant: " + LogSanitizer.sanitize(tenantDomain), e);
            return null;
        }
    }

    /**
     * Resolves a username given either a username or a user id - preferring the username when
     * present (a citizen self-service complaint can have a {@code null} username; see
     * {@code Complaint#getUserName()}, only populated when the portal app's OIDC client is
     * configured with a username access-token attribute), falling back to resolving it from the
     * stored user id otherwise.
     */
    private static Optional<String> resolveUsername(AbstractUserStoreManager userStoreManager, String userId,
            String userName) {
        if (userName != null && !userName.trim().isEmpty()) {
            return Optional.of(userName.trim());
        }
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String resolved = userStoreManager.getUserNameFromUserID(userId.trim());
            return (resolved == null || resolved.trim().isEmpty()) ? Optional.empty() : Optional.of(resolved.trim());
        } catch (Exception e) {
            LOG.error("Error resolving username for user id '" + LogSanitizer.sanitize(userId) + "'", e);
            return Optional.empty();
        }
    }

    /**
     * Falls back to the username itself when the email claim is blank but the username looks like
     * an email address - a common setup on user stores configured with email-as-username, where
     * the claim is otherwise never populated separately.
     */
    private static Optional<String> resolveEmail(AbstractUserStoreManager userStoreManager, String username) {
        try {
            String email = userStoreManager.getUserClaimValue(username, EMAIL_CLAIM, null);
            if (email != null && !email.trim().isEmpty()) {
                return Optional.of(email.trim());
            }
            if (EmailValidator.isEmail(username)) {
                LOG.debug("No email claim resolvable for user '" + LogSanitizer.sanitize(username)
                        + "'; falling back to the username, which looks like an email address.");
                return Optional.of(username);
            }
            LOG.debug("No email claim resolvable for user '" + LogSanitizer.sanitize(username) + "'");
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Error resolving email claim for user '" + LogSanitizer.sanitize(username) + "'", e);
            return Optional.empty();
        }
    }

    private static String resolveDisplayName(AbstractUserStoreManager userStoreManager, String username) {
        try {
            String givenName = userStoreManager.getUserClaimValue(username, GIVEN_NAME_CLAIM, null);
            String lastName = userStoreManager.getUserClaimValue(username, LAST_NAME_CLAIM, null);
            String displayName = ((givenName == null ? "" : givenName.trim()) + " "
                    + (lastName == null ? "" : lastName.trim())).trim();
            return displayName.isEmpty() ? username : displayName;
        } catch (Exception e) {
            LOG.error("Error resolving display name for user '" + LogSanitizer.sanitize(username)
                    + "'; falling back to the username.", e);
            return username;
        }
    }

    /**
     * Computes every display-ready placeholder the HTML template references - the same values for
     * every recipient of a given event, since they describe the complaint/comment itself rather
     * than who's reading it. {@code event} is {@code null} for a brand-new complaint (the actor is
     * always the creator, there is no comment).
     */
    private Map<String, Object> buildTemplatePlaceholders(Complaint complaint, ComplaintEvent event,
            boolean notifyingCreator, String notificationType) {

        AbstractUserStoreManager userStoreManager = resolveUserStoreManager(complaint.getOrgId());

        String dataPrincipalName = userStoreManager == null ? ROLE_LABEL_DATA_PRINCIPAL
                : resolveUsername(userStoreManager, complaint.getUserId(), complaint.getUserName())
                        .map(username -> resolveDisplayName(userStoreManager, username))
                        .orElse(ROLE_LABEL_DATA_PRINCIPAL);

        boolean isCommentAdded = NOTIFICATION_TYPE_COMMENT_ADDED.equals(notificationType);
        String actorName;
        if (!isCommentAdded) {
            // A new complaint was filed - the citizen who filed it is both the actor and the
            // data principal.
            actorName = dataPrincipalName;
        } else if (notifyingCreator) {
            actorName = userStoreManager == null ? ROLE_LABEL_GRIEVANCE_OFFICER
                    : resolveUsername(userStoreManager, event.getActorUserId(), event.getActorUserName())
                            .map(username -> resolveDisplayName(userStoreManager, username))
                            .orElse(ROLE_LABEL_GRIEVANCE_OFFICER);
        } else {
            actorName = dataPrincipalName;
        }

        String status = complaint.getStatus();
        String statusLabel = STATUS_LABELS.getOrDefault(status, humanize(status));

        String deepLinkPath = notifyingCreator ? CREATOR_DEEP_LINK_PATH : OFFICER_DEEP_LINK_PATH;
        String actionUrl = IdentityUtil.getServerURL(deepLinkPath + complaint.getComplaintId(), true, false);

        String referenceId = complaint.getReferenceId();
        String verb = isCommentAdded ? "replied to complaint" : "filed a new complaint";
        String headlineHtml = "<strong>" + htmlEscape(actorName) + "</strong> " + verb + " <strong>"
                + htmlEscape(referenceId) + "</strong>.";
        String footerText = notifyingCreator
                ? "You're receiving this because you filed this complaint."
                : "You're receiving this because you're the assigned Grievance Officer - this is an "
                        + "automated notification, please don't reply directly to it.";
        String actionBadgeHtml = buildActionBadge(status, notifyingCreator);
        String messageExcerpt = isCommentAdded ? excerpt(event.getComment()) : excerpt(complaint.getDescription());

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put(PROP_REFERENCE_ID, referenceId);
        placeholders.put(PROP_MESSAGE_EXCERPT, messageExcerpt);
        placeholders.put(PLACEHOLDER_DATA_PRINCIPAL_NAME, dataPrincipalName);
        placeholders.put(PLACEHOLDER_ACTOR_NAME, actorName);
        placeholders.put(PLACEHOLDER_CATEGORY_LABEL, humanize(complaint.getCategory()));
        placeholders.put(PLACEHOLDER_PRIORITY_LABEL, humanize(complaint.getPriority()));
        placeholders.put(PLACEHOLDER_STATUS_LABEL, statusLabel);
        placeholders.put(PLACEHOLDER_SLA_LABEL, slaLabel(complaint.getStatutoryDueTime()));
        placeholders.put(PLACEHOLDER_ACTION_URL, actionUrl);
        placeholders.put(PLACEHOLDER_RECIPIENT_ROLE_LABEL,
                notifyingCreator ? ROLE_LABEL_DATA_PRINCIPAL : ROLE_LABEL_GRIEVANCE_OFFICER);
        placeholders.put(PLACEHOLDER_HEADLINE_HTML, headlineHtml);
        placeholders.put(PLACEHOLDER_FOOTER_TEXT, footerText);
        placeholders.put(PLACEHOLDER_ACTION_BADGE_HTML, actionBadgeHtml);
        placeholders.put(PLACEHOLDER_ACTION_BUTTON_HTML, buildActionButton(actionUrl));
        placeholders.put(PLACEHOLDER_LOGO_URL, IdentityUtil.getServerURL(LOGO_PATH, true, false));
        return placeholders;
    }

    /**
     * Whether this specific recipient needs to act right now depends on the complaint's *new*
     * status, not a fixed label: WAITING_ON_CLIENT means the citizen needs to act, every other
     * open status means the officer does, and RESOLVED means neither does.
     */
    private static String buildActionBadge(String status, boolean notifyingCreator) {
        if (STATUS_RESOLVED.equals(status)) {
            return buildBadge("Resolved", "#dcfce7", "#166534");
        }
        boolean isCitizensTurn = STATUS_WAITING_ON_CLIENT.equals(status);
        boolean isThisRecipientsTurn = notifyingCreator == isCitizensTurn;
        return isThisRecipientsTurn ? buildBadge("Action Needed", "#fee2e2", "#b91c1c")
                : buildBadge("Update", "#f3f4f6", "#4b5563");
    }

    private static String buildBadge(String text, String background, String color) {
        return "<span style=\"display:inline-block;background-color:" + background + ";color:" + color
                + ";font-size:11px;font-weight:700;letter-spacing:0.04em;text-transform:uppercase;"
                + "padding:4px 10px;border-radius:999px;\">" + text + "</span>";
    }

    /**
     * The call to action is per-notification-type rather than part of the shared shell, because it
     * tells the recipient to act - true for the officer's "new complaint" mail and for either
     * side's "new reply" mail, but not for the acknowledgement, which confirms receipt of the
     * citizen's own complaint and quotes their own description back at them. A "Review & Reply"
     * button there asks them to reply to themselves. The shell's footer link is on
     * {@code action-url} regardless, so an acknowledgement with no button still offers a way in.
     * <p>
     * The URL is interpolated here rather than left as an {@code action-url} placeholder inside
     * this markup: IS's notification handler substitutes the template in a single pass, so a
     * placeholder occurring within another placeholder's *value* is never expanded and would ship
     * as literal text in the href.
     */
    private static String buildActionButton(String actionUrl) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" align=\"center\" "
                + "style=\"margin:0 auto;\"><tr>"
                + "<td style=\"border-radius:24px;background-color:#f97316;\">"
                + "<a href=\"" + actionUrl + "\" style=\"display:inline-block;padding:12px 32px;"
                + "font-size:14px;font-weight:700;color:#ffffff;text-decoration:none;"
                + "border-radius:24px;\">Review &amp; Reply</a>"
                + "</td></tr></table>";
    }

    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Map<String, String> buildStatusLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("OPEN", "Open");
        labels.put("IN_PROGRESS", "In Progress");
        labels.put("WAITING_ON_CLIENT", "Waiting on Client");
        labels.put("AWAITING_INTERNAL_REVIEW", "Waiting on Internal Review");
        labels.put("RESOLVED", "Resolved");
        return labels;
    }

    /** {@code "UNAUTHORIZED_DATA_SHARING"} / {@code "CRITICAL"} -> {@code "Unauthorized Data Sharing"} / {@code "Critical"}. */
    private static String humanize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String word : value.trim().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return result.toString();
    }

    private static String slaLabel(long statutoryDueTimeMillis) {
        long remainingMillis = statutoryDueTimeMillis - System.currentTimeMillis();
        long remainingDays = (long) Math.ceil(remainingMillis / (double) TimeUnit.DAYS.toMillis(1));
        if (remainingDays > 1) {
            return remainingDays + " days left";
        } else if (remainingDays == 1) {
            return "1 day left";
        } else if (remainingDays == 0) {
            return "Due today";
        }
        long overdueDays = Math.abs(remainingDays);
        return "Overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days");
    }

    private static String excerpt(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_EXCERPT_LENGTH ? message : message.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }

    /**
     * Fires a standard {@code TRIGGER_NOTIFICATION} event for one recipient - the compulsory
     * attributes IS's own notification handler expects are {@code send-to}, {@code user-name} and
     * a template type.
     */
    private void triggerNotification(Recipient recipient, String tenantDomain, String notificationType,
            Map<String, Object> placeholders) {
        IdentityEventService eventService = eventServiceSupplier.get();
        if (eventService == null) {
            LOG.debug("IdentityEventService is not resolvable; complaint notification not sent.");
            return;
        }
        Map<String, Object> triggerProperties = new HashMap<>(placeholders);
        triggerProperties.put(TRIGGER_PROP_SEND_TO, recipient.getEmail());
        triggerProperties.put(IdentityEventConstants.EventProperty.USER_NAME, recipient.getUsername());
        triggerProperties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
        triggerProperties.put(TRIGGER_PROP_TEMPLATE_TYPE, notificationType);
        try {
            eventService.handleEvent(new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION,
                    triggerProperties));
        } catch (Throwable t) {
            // Deliberately never rethrown - see interface javadoc.
            LOG.error("Error triggering '" + notificationType + "' notification email to '"
                    + LogSanitizer.sanitize(recipient.getEmail()) + "' in tenant: "
                    + LogSanitizer.sanitize(tenantDomain), t);
        }
    }

    /** A resolved notification recipient - a username paired with the email address to send to. */
    private static final class Recipient {

        private final String username;
        private final String email;

        Recipient(String username, String email) {
            this.username = username;
            this.email = email;
        }

        String getUsername() {
            return username;
        }

        String getEmail() {
            return email;
        }
    }
}
