package com.parknova.parknovaapigateway.auth.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakTokenService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenService.class);

    private final RestClient restClient;
    private final ParknovaProperties properties;
    private final ObjectMapper objectMapper;

    public KeycloakTokenService(RestClient restClient, ParknovaProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenResponse login(String username, String password) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return postToken(form, "Invalid email or password");
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form, "Session expired, please log in again");
    }

    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("refresh_token", refreshToken);

        try {
            restClient.post()
                    .uri(properties.keycloak().logoutUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Keycloak logout completed");
        } catch (HttpClientErrorException ex) {
            log.warn("Keycloak logout failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            // Fallback: try token revocation endpoint
            revokeRefreshToken(refreshToken);
        }
    }

    private void revokeRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post()
                    .uri(properties.keycloak().revokeUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to revoke session");
        }
    }

    private TokenResponse postToken(MultiValueMap<String, String> form, String failMessage) {
        try {
            TokenResponse response = restClient.post()
                    .uri(properties.keycloak().tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, failMessage);
            }
            return response;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Keycloak token request failed: {}", body);
            throw new ApiException(HttpStatus.UNAUTHORIZED, mapTokenError(body, failMessage));
        } catch (HttpClientErrorException ex) {
            log.error("Keycloak token error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Authentication service unavailable");
        }
    }

    private String mapTokenError(String body, String fallback) {
        String description = readErrorDescription(body);
        if (description == null || description.isBlank()) {
            return fallback;
        }
        String normalized = description.toLowerCase();
        if (normalized.contains("not fully set up") || normalized.contains("required action")) {
            return "Account is not fully set up in Keycloak (required action pending). "
                    + "Clear Required user actions on the user in Keycloak Admin Console.";
        }
        if (normalized.contains("invalid_grant") || normalized.contains("invalid user credentials")) {
            return fallback;
        }
        return description;
    }

    private String readErrorDescription(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode description = node.get("error_description");
            if (description != null && !description.isNull()) {
                return description.asText();
            }
            JsonNode error = node.get("error");
            return error != null && !error.isNull() ? error.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private MultiValueMap<String, String> baseClientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.keycloak().clientId());
        form.add("client_secret", properties.keycloak().clientSecret());
        return form;
    }
}
