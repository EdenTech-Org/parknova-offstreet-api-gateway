package com.parknova.parknovaapigateway.auth.orgadmin;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class OrgAdminInviteClient {

    public static final String SERVICE_HEADER = "X-SERVICE-TO-SERVICE";

    private final RestClient restClient;
    private final ParknovaProperties properties;

    public OrgAdminInviteClient(ParknovaProperties properties) {
        this.properties = properties;
        String base = properties.orgAdminBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.restClient = RestClient.builder().baseUrl(base).build();
    }

    public PortalInviteRecord create(
            String token,
            long organizationId,
            String organizationName,
            String email,
            String keycloakUserId,
            String kind,
            Instant expiresAt
    ) {
        Map<String, Object> body = Map.of(
                "token", token,
                "organizationId", organizationId,
                "organizationName", organizationName != null ? organizationName : "",
                "email", email,
                "keycloakUserId", keycloakUserId,
                "kind", kind,
                "expiresAtEpochSeconds", expiresAt.getEpochSecond()
        );
        try {
            PortalInviteRecord created = restClient.post()
                    .uri("/internal/v1/portal-invites")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SERVICE_HEADER, properties.serviceToServiceSecret())
                    .body(body)
                    .retrieve()
                    .body(PortalInviteRecord.class);
            if (created == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Org-admin invite create returned empty body");
            }
            return created;
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to store portal invite in org-admin: HTTP " + ex.getStatusCode().value());
        }
    }

    public Optional<PortalInviteRecord> findByToken(String token) {
        try {
            PortalInviteRecord record = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/portal-invites/by-token")
                            .queryParam("token", token)
                            .build())
                    .header(SERVICE_HEADER, properties.serviceToServiceSecret())
                    .retrieve()
                    .body(PortalInviteRecord.class);
            return Optional.ofNullable(record);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to load portal invite from org-admin: HTTP " + ex.getStatusCode().value());
        }
    }

    public PortalInviteRecord consume(String token) {
        try {
            PortalInviteRecord record = restClient.post()
                    .uri("/internal/v1/portal-invites/{token}/consume", token)
                    .header(SERVICE_HEADER, properties.serviceToServiceSecret())
                    .retrieve()
                    .body(PortalInviteRecord.class);
            if (record == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Org-admin invite consume returned empty body");
            }
            return record;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has already been used. You can request a new link.");
        } catch (HttpClientErrorException.BadRequest ex) {
            String body = ex.getResponseBodyAsString();
            if (body != null && body.toLowerCase().contains("expired")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This link has expired. You can request a new link.");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This link is invalid or has already been used. You can request a new link.");
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to consume portal invite in org-admin: HTTP " + ex.getStatusCode().value());
        }
    }

    public record PortalInviteRecord(
            String token,
            Long organizationId,
            String organizationName,
            String email,
            String keycloakUserId,
            String kind,
            Instant expiresAt,
            Instant usedAt,
            Instant invalidatedAt,
            boolean expired,
            boolean usable
    ) {
    }
}
