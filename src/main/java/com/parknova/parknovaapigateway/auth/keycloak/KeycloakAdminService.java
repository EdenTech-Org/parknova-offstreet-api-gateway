package com.parknova.parknovaapigateway.auth.keycloak;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    public static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String ATTR_ORGANIZATION_ID = "organizationId";
    public static final String ATTR_ORGANIZATION_NAME = "organizationName";
    public static final String ATTR_INVITE_TOKEN = "inviteToken";
    public static final String ATTR_INVITE_EXPIRES_AT = "inviteExpiresAt";
    public static final String ATTR_INVITE_KIND = "inviteKind";
    public static final String INVITE_KIND_SETUP = "SETUP";
    public static final String INVITE_KIND_RESET = "RESET";

    private static final String FORBIDDEN_HINT =
            "Keycloak Admin API returned 403 Forbidden for client '%s'. "
                    + "In Keycloak: Clients -> client -> Capability config: Client authentication ON, Service accounts ON; "
                    + "Client scopes: Full scope allowed ON; Service account roles -> Assign role -> Filter by clients -> realm-management -> "
                    + "manage-users, view-users, query-users, view-realm (required to assign SYSTEM_ADMIN), or realm-admin.";

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
        // Email is not unique in this realm (duplicateEmailsAllowed=true), so no email-uniqueness check.

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

    /**
     * Creates a tenant system admin with no password. Invite tokens are stored in org-admin DB.
     */
    public ProvisionResult provisionTenantAdmin(
            String username, String email, long organizationId, String organizationName) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase();
        // Identity is username-based (email may be duplicated), so dedup on username, not email.
        Optional<UserRepresentation> existing = findByUsername(normalizedUsername);
        if (existing.isPresent()) {
            UserRepresentation user = existing.get();
            if (hasRealmRole(user.getId(), ROLE_SYSTEM_ADMIN)
                    && organizationIdEquals(user, organizationId)
                    && !hasPasswordCredential(user.getId())) {
                ensureOrgAttributes(user.getId(), organizationId, organizationName);
                return new ProvisionResult(user.getId(), false);
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "Username already registered in Keycloak; cannot provision tenant admin");
        }

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setFirstName("Tenant");
        user.setLastName("Admin");
        user.setEmailVerified(false);
        user.setRequiredActions(List.of());
        user.setCredentials(List.of());
        user.setAttributes(orgAttributes(organizationId, organizationName));

        UsersResource users = users();
        try (Response response = adminCall(() -> users.create(user))) {
            int status = response.getStatus();
            if (status == 201) {
                String userId = CreatedResponseUtil.getCreatedId(response);
                assignRealmRole(userId, ROLE_SYSTEM_ADMIN);
                // Create can drop custom attrs when User profile blocks unmanaged attributes — force-set and verify.
                ensureOrgAttributes(userId, organizationId, organizationName);
                UserRepresentation stored = findById(userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY,
                                "Provisioned Keycloak user not found after create"));
                if (!organizationIdEquals(stored, organizationId)) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "Keycloak did not persist user attribute organizationId. "
                                    + "In Realm settings → User profile: allow unmanaged attributes, "
                                    + "or declare attribute organizationId. Then delete this user and re-provision.");
                }
                log.info("Provisioned tenant admin userId={} organizationId={} email={}",
                        userId, organizationId, normalizedEmail);
                return new ProvisionResult(userId, true);
            }
            if (status == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "User already exists in Keycloak");
            }
            if (status == 403) {
                throw forbidden();
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Failed to provision tenant admin in Keycloak: HTTP " + status);
        }
    }

    public void ensureOrgAttributes(String userId, long organizationId, String organizationName) {
        UserResource resource = users().get(userId);
        UserRepresentation user = adminCall(resource::toRepresentation);
        Map<String, List<String>> attrs = user.getAttributes() != null
                ? new HashMap<>(user.getAttributes())
                : new HashMap<>();
        attrs.put(ATTR_ORGANIZATION_ID, List.of(String.valueOf(organizationId)));
        if (organizationName != null && !organizationName.isBlank()) {
            attrs.put(ATTR_ORGANIZATION_NAME, List.of(organizationName.trim()));
        } else {
            attrs.remove(ATTR_ORGANIZATION_NAME);
        }
        // Clear legacy invite attrs if present from older deployments
        attrs.remove(ATTR_INVITE_TOKEN);
        attrs.remove(ATTR_INVITE_EXPIRES_AT);
        attrs.remove(ATTR_INVITE_KIND);
        user.setAttributes(attrs);
        adminCall(() -> {
            resource.update(user);
            return null;
        });
    }

    public void activateWithPassword(String userId, String password) {
        UserResource resource = users().get(userId);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(false);
        credential.setValue(password);

        adminCall(() -> {
            resource.resetPassword(credential);
            UserRepresentation user = resource.toRepresentation();
            Map<String, List<String>> attrs = user.getAttributes() != null
                    ? new HashMap<>(user.getAttributes())
                    : new HashMap<>();
            attrs.remove(ATTR_INVITE_TOKEN);
            attrs.remove(ATTR_INVITE_EXPIRES_AT);
            attrs.remove(ATTR_INVITE_KIND);
            user.setAttributes(attrs);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setRequiredActions(List.of());
            resource.update(user);
            return null;
        });
        log.info("Activated tenant admin userId={}", userId);
    }

    public void changePassword(String userId, String newPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(false);
        credential.setValue(newPassword);
        adminCall(() -> {
            users().get(userId).resetPassword(credential);
            return null;
        });
    }

    public void logoutOtherSessions(String userId, String keepSessionId) {
        UserResource resource = users().get(userId);
        List<UserSessionRepresentation> sessions = adminCall(resource::getUserSessions);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        RealmResource realm = realm();
        for (UserSessionRepresentation session : sessions) {
            if (keepSessionId != null && keepSessionId.equals(session.getId())) {
                continue;
            }
            try {
                adminCall(() -> {
                    realm.deleteSession(session.getId(), false);
                    return null;
                });
            } catch (RuntimeException ex) {
                log.warn("Failed to delete Keycloak session {} for user {}: {}",
                        session.getId(), userId, ex.getMessage());
            }
        }
    }

    public boolean hasRealmRole(String userId, String roleName) {
        List<RoleRepresentation> roles = adminCall(
                () -> users().get(userId).roles().realmLevel().listAll());
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(r -> roleName.equals(r.getName()));
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

    public Optional<UserRepresentation> findById(String userId) {
        try {
            return Optional.ofNullable(adminCall(() -> users().get(userId).toRepresentation()));
        } catch (WebApplicationException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public boolean hasPasswordCredential(String userId) {
        List<CredentialRepresentation> credentials = adminCall(() -> users().get(userId).credentials());
        if (credentials == null) {
            return false;
        }
        return credentials.stream()
                .anyMatch(c -> CredentialRepresentation.PASSWORD.equals(c.getType()));
    }

    private void assignRealmRole(String userId, String roleName) {
        RoleRepresentation role = adminCall(() -> realm().roles().get(roleName).toRepresentation());
        adminCall(() -> {
            users().get(userId).roles().realmLevel().add(List.of(role));
            return null;
        });
    }

    private Map<String, List<String>> orgAttributes(long organizationId, String organizationName) {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put(ATTR_ORGANIZATION_ID, List.of(String.valueOf(organizationId)));
        if (organizationName != null && !organizationName.isBlank()) {
            attrs.put(ATTR_ORGANIZATION_NAME, List.of(organizationName.trim()));
        }
        return attrs;
    }

    private boolean organizationIdEquals(UserRepresentation user, long organizationId) {
        String raw = firstAttr(user, ATTR_ORGANIZATION_ID);
        return raw != null && raw.equals(String.valueOf(organizationId));
    }

    public static String firstAttr(UserRepresentation user, String key) {
        if (user.getAttributes() == null) {
            return null;
        }
        List<String> values = user.getAttributes().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private UsersResource users() {
        return realm().users();
    }

    private RealmResource realm() {
        return keycloak.realm(properties.keycloak().realm());
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

    public record ProvisionResult(String userId, boolean created) {
    }
}
