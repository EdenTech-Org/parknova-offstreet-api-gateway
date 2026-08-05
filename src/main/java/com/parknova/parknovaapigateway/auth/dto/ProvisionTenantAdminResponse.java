package com.parknova.parknovaapigateway.auth.dto;

public record ProvisionTenantAdminResponse(
        String message,
        String email,
        Long organizationId
) {
}
