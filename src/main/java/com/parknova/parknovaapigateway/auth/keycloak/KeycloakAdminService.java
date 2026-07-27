package com.parknova.parknovaapigateway.auth.keycloak;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private static final String FORBIDDEN_HINT =
            "Keycloak Admin API returned 403 Forbidden for client '%s'. "
                    + "In Keycloak: Clients -> client -> Capability config: Client authentication ON, Service accounts ON; "
                    + "Client scopes: Full scope allowed ON; Service account roles -> Assign role -> Filter by clients -> realm-management -> "
                    + "manage-users, view-users, query-users (or realm-admin).";

    private final Keycloak keycloak;
    private final ParknovaProperties properties;

    public KeycloakAdminService(Keycloak keycloak, ParknovaProperties properties) {
        this.keycloak = keycloak;
        this.properties = properties;
    }

    public String createUser(
            String username,
            String email,
            String firstName,
            String lastName,
            String phone,
            String password
    ) {
        if (findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmailVerified(false);
        user.setRequiredActions(List.of());
        user.setAttributes(Map.of("phone", List.of(phone)));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(false);
        credential.setValue(password);
        user.setCredentials(List.of(credential));

        UsersResource users = users();
        try (Response response = adminCall(() -> users.create(user))) {
            int status = response.getStatus();
            if (status == 201) {
                String userId = CreatedResponseUtil.getCreatedId(response);
                UserResource created = users.get(userId);
                adminCall(() -> {
                    UserRepresentation representation = created.toRepresentation();
                    representation.setFirstName(firstName);
                    representation.setLastName(lastName);
                    representation.setRequiredActions(List.of());
                    representation.setEmailVerified(false);
                    created.update(representation);
                    created.resetPassword(credential);
                    return null;
                });
                log.info("Created Keycloak user id={} username={}", userId, username);
                return userId;
            }
            if (status == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "User already exists in Keycloak");
            }
            if (status == 403) {
                throw forbidden();
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to create user in Keycloak: HTTP " + status);
        }
    }

    public Optional<UserRepresentation> findByEmail(String email) {
        List<UserRepresentation> users = adminCall(
                () -> users().searchByEmail(email.trim().toLowerCase(), true));
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        return users.stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    public Optional<UserRepresentation> findByUsername(String username) {
        List<UserRepresentation> users = adminCall(() -> users().search(username, true));
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        return users.stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    private UsersResource users() {
        return keycloak.realm(properties.keycloak().realm()).users();
    }

    private <T> T adminCall(Supplier<T> call) {
        try {
            return call.get();
        } catch (WebApplicationException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatus() == 403) {
                throw forbidden();
            }
            throw ex;
        }
    }

    private ApiException forbidden() {
        String clientId = properties.keycloak().clientId();
        log.error("Keycloak Admin API forbidden for client={}", clientId);
        return new ApiException(HttpStatus.BAD_GATEWAY, FORBIDDEN_HINT.formatted(clientId));
    }
}
