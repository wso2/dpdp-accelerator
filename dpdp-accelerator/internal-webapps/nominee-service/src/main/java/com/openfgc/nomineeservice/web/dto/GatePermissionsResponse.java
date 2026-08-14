package com.openfgc.nomineeservice.web.dto;

import java.util.List;

/**
 * The permissions currently in force for an (owner, nominee) pair. The list is
 * empty whenever {@code active} is false.
 */
public record GatePermissionsResponse(boolean active, List<String> permissions) {

    public GatePermissionsResponse {
        permissions = List.copyOf(permissions);
    }
}
