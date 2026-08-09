package com.parknova.parknovaapigateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProvisionTenantAdminRequest(
        @NotNull @Positive Long organizationId,
        @NotBlank String username,
        @NotBlank @Email String email
) {
}
