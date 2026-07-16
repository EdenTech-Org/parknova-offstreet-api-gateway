package com.parknova.parknovaapigateway.auth.keycloak;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
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

@Component
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final Keycloak keycloak;
    private final ParknovaProperties properties;

    public KeycloakAdminService(Keycloak keycloak, ParknovaProperties properties) {
        this.keycloak = keycloak;
        this.properties = properties;
    }

    public String createUser(String username, String email, String phone, String password) {
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
        user.setEmailVerified(false);
        user.setAttributes(Map.of("phone", List.of(phone)));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(false);
        credential.setValue(password);
        user.setCredentials(List.of(credential));

        UsersResource users = users();
        try (Response response = users.create(user)) {
            int status = response.getStatus();
            if (status == 201) {
                String userId = CreatedResponseUtil.getCreatedId(response);
                log.info("Created Keycloak user id={} username={}", userId, username);
                return userId;
            }
            if (status == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "User already exists in Keycloak");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to create user in Keycloak: HTTP " + status);
        }
    }

    public void markEmailVerified(String email) {
        UserRepresentation user = requireByEmail(email);
        user.setEmailVerified(true);
        users().get(user.getId()).update(user);
        log.info("Marked email verified for userId={}", user.getId());
    }

    public void resetPassword(String email, String newPassword) {
        UserRepresentation user = requireByEmail(email);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(false);
        credential.setValue(newPassword);
        users().get(user.getId()).resetPassword(credential);
        log.info("Password reset for userId={}", user.getId());
    }

    public void logoutAllSessions(String email) {
        UserRepresentation user = requireByEmail(email);
        UserResource resource = users().get(user.getId());
        resource.logout();
        log.info("Logged out all sessions for userId={}", user.getId());
    }

    public boolean isEmailVerified(String usernameOrEmail) {
        Optional<UserRepresentation> byUsername = findByUsername(usernameOrEmail);
        if (byUsername.isPresent()) {
            return Boolean.TRUE.equals(byUsername.get().isEmailVerified());
        }
        return findByEmail(usernameOrEmail)
                .map(u -> Boolean.TRUE.equals(u.isEmailVerified()))
                .orElse(false);
    }

    public Optional<UserRepresentation> findByEmail(String email) {
        List<UserRepresentation> users = users().searchByEmail(email.trim().toLowerCase(), true);
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        return users.stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    public Optional<UserRepresentation> findByUsername(String username) {
        List<UserRepresentation> users = users().search(username, true);
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        return users.stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    private UserRepresentation requireByEmail(String email) {
        return findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UsersResource users() {
        return keycloak.realm(properties.keycloak().realm()).users();
    }
}
