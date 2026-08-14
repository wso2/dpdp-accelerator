package com.openfgc.nomineeservice.web.dto;

import com.openfgc.nomineeservice.domain.Nomination;
import com.openfgc.nomineeservice.domain.NominationStatus;
import com.openfgc.nomineeservice.domain.NomineePermission;
import java.time.Instant;
import java.util.Set;

/** One nomination as returned to the owner, the nominee, or an administrator. */
public record NominationResponse(
        String id,
        String ownerId,
        String nomineeId,
        String nomineeEmail,
        Set<NomineePermission> permissions,
        NominationStatus status,
        Instant nominatedAt,
        Instant acceptedAt,
        String activatedBy,
        Instant activatedAt,
        String activationTicket) {

    public NominationResponse {
        permissions = Set.copyOf(permissions);
    }

    public static NominationResponse from(Nomination nomination) {
        return new NominationResponse(
                nomination.getId(),
                nomination.getOwnerId(),
                nomination.getNomineeId(),
                nomination.getNomineeEmail(),
                nomination.getPermissions(),
                nomination.getStatus(),
                nomination.getNominatedAt(),
                nomination.getAcceptedAt(),
                nomination.getActivatedBy(),
                nomination.getActivatedAt(),
                nomination.getActivationTicket());
    }
}
