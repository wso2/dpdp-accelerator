package com.openfgc.nomineeservice.web.dto;

import com.openfgc.nomineeservice.domain.NomineePermission;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** Replaces the permissions granted to one nominee. */
public record UpdatePermissionsRequest(@NotEmpty Set<NomineePermission> permissions) {
}
