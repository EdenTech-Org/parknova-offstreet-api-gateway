package com.parknova.parknovaapigateway.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SetupPasswordRequest(
        @NotBlank String token,
        @NotBlank String password,
        @NotBlank String confirmPassword
) {
}
