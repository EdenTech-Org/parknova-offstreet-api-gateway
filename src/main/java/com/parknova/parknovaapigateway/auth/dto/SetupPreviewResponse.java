package com.parknova.parknovaapigateway.auth.dto;

public record SetupPreviewResponse(
        String email,
        Long organizationId,
        String kind
) {
}
