package com.parknova.parknovaapigateway.auth.dto;

public record SetupPreviewResponse(
        String email,
        String organizationName,
        Long organizationId,
        String kind
) {
}
