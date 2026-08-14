package com.openfgc.nomineeservice.web.dto;

import com.openfgc.nomineeservice.domain.NomineePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record CreateNominationRequest(
        @NotNull String nomineeId,
        @NotNull @Email String nomineeEmail,
        @NotEmpty Set<NomineePermission> permissions) {
}
