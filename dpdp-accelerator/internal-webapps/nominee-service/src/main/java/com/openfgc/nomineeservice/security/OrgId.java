package com.openfgc.nomineeservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the organization a request belongs to.
 *
 * <p>Every table in OpenFGC carries an organization id, so every row this
 * service writes needs one. It comes from the caller's token or from
 * configuration, and never from the request body: a value the browser can set is
 * not a value that can scope data. This is the same rule that already applies to
 * the owner id.
 *
 * <p>The override exists because a deployment's tokens may carry no organization
 * claim at all, which is why the portal backend has an equivalent
 * {@code ORG_ID_OVERRIDE}. Without it, this service would write rows under
 * {@link #DEFAULT} while the consent server wrote the same users' data under a
 * real organization, and the two databases would not line up.
 */
@Component
public class OrgId {

    /**
     * Used when neither the override nor the claim yields a value. Matches the
     * column default in the consent server schema, so a deployment that has not
     * configured organizations still produces consistent rows across services.
     */
    public static final String DEFAULT = "DEFAULT_ORG";

    private final String claimName;
    private final String override;

    public OrgId(@Value("${organization.id-claim:org_id}") String claimName,
                 @Value("${organization.id-override:}") String override) {
        this.claimName = claimName;
        this.override = override == null ? "" : override.trim();
    }

    /** The organization for this caller, never null and never blank. */
    public String from(Jwt jwt) {
        if (!override.isEmpty()) {
            return override;
        }
        if (jwt != null) {
            String claim = jwt.getClaimAsString(claimName);
            if (claim != null && !claim.isBlank()) {
                return claim.trim();
            }
        }
        return DEFAULT;
    }
}
