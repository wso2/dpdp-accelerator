package com.openfgc.nomineeservice.web.dto;

import com.openfgc.nomineeservice.domain.NomineeAuditEvent.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * An event to append to the audit chain, reported by whichever service observed
 * it. The nomination is resolved from the owner-nominee pair rather than
 * supplied, so a caller cannot attribute an event to an unrelated nomination.
 */
public record RecordAuditRequest(
        @NotBlank String ownerId,
        @NotBlank String nomineeId,
        @NotNull EventType event,
        String detail) {
}
