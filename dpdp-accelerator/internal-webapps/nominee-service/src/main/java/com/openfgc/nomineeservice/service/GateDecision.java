package com.openfgc.nomineeservice.service;

import com.openfgc.nomineeservice.domain.NomineePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The answer to "may this nominee act for this owner, and to what extent?"
 *
 * <p>An inactive nomination carries no permissions, so a caller cannot read a
 * permission set without also seeing that it does not currently apply.
 */
public record GateDecision(boolean active, Set<NomineePermission> permissions) {

    private static final GateDecision DENIED = new GateDecision(false, Set.of());

    public GateDecision {
        permissions = permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    /** No nomination, or one that is not currently active. */
    public static GateDecision denied() {
        return DENIED;
    }

    public static GateDecision allowed(Set<NomineePermission> permissions) {
        return new GateDecision(true, permissions);
    }

    public boolean grants(NomineePermission permission) {
        return active && permissions.contains(permission);
    }
}
