package com.openfgc.nomineeservice.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What an owner may grant a nominee. Each value is granted individually, so a
 * nominee can be allowed to view consents without being allowed to revoke them.
 *
 * <p>These values are part of the API contract and are mirrored by the portal
 * frontend in {@code types/nominee.ts}.
 */
public enum NomineePermission {

    CONSENT_VIEW,

    /** Requires {@link #CONSENT_VIEW}: a consent has to be found before it can be revoked. */
    CONSENT_REVOKE(CONSENT_VIEW),

    /**
     * Approve a consent the owner has pending. Separate from {@link #CONSENT_REVOKE}
     * because the two are opposite acts: revoking withdraws processing the owner
     * already chose, while approving authorises new processing on their behalf.
     * An owner granting one must not silently confer the other.
     *
     * <p>Requires {@link #CONSENT_VIEW}: a consent has to be found before it can
     * be approved.
     */
    CONSENT_APPROVE(CONSENT_VIEW),

    ACCOUNT_VIEW,

    /** Requires {@link #ACCOUNT_VIEW}: a profile has to be read before it can be changed. */
    ACCOUNT_UPDATE(ACCOUNT_VIEW),

    /** Requires {@link #ACCOUNT_VIEW}: an account has to be identifiable before it is closed. */
    ACCOUNT_DELETE(ACCOUNT_VIEW);

    private final Set<NomineePermission> requires;

    // Set.of rather than EnumSet: this runs during class initialisation, before
    // the enum is complete, and EnumSet rejects a type it cannot yet resolve.
    NomineePermission(NomineePermission... requires) {
        this.requires = Set.of(requires);
    }

    /**
     * Adds whatever the requested permissions depend on.
     *
     * <p>An owner granting only {@code CONSENT_REVOKE} means the nominee may
     * revoke, but a nominee who cannot list consents can never reach one to
     * revoke it. Rather than store a grant that cannot be exercised, the implied
     * permission is added: the owner's intent is honoured and every stored grant
     * describes something the nominee can actually do.
     */
    public static Set<NomineePermission> expand(Set<NomineePermission> granted) {
        if (granted.isEmpty()) {
            return Set.of();
        }
        EnumSet<NomineePermission> complete = EnumSet.copyOf(granted);
        // One pass suffices while no dependency itself depends on another; the
        // loop re-checks so a deeper chain added later still resolves.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NomineePermission permission : EnumSet.copyOf(complete)) {
                changed |= complete.addAll(permission.requires);
            }
        }
        return Collections.unmodifiableSet(complete);
    }
}
