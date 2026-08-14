package com.openfgc.nomineeservice.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DeactivateRequest(@NotBlank String reason) {
}
