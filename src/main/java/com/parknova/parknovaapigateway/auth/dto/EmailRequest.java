package com.parknova.parknovaapigateway.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the portal admin's username (identity is username-based; email is no longer unique). */
public record EmailRequest(
        @NotBlank String username
) {
}
