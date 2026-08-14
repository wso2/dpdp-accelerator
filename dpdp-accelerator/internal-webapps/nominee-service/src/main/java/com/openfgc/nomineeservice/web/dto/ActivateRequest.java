package com.openfgc.nomineeservice.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateRequest(@NotBlank String ticketReference) {
}
