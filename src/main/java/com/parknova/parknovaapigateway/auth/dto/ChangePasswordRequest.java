package com.parknova.parknovaapigateway.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {
}
