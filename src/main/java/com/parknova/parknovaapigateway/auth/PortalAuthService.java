package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.ChangePasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.EmailRequest;
import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminRequest;
import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminResponse;
import com.parknova.parknovaapigateway.auth.dto.SetupPasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.SetupPreviewResponse;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakAdminService;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakAdminService.ProvisionResult;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakTokenService;
import com.parknova.parknovaapigateway.auth.orgadmin.OrgAdminInviteClient;
import com.parknova.parknovaapigateway.auth.orgadmin.OrgAdminInviteClient.PortalInviteRecord;
import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class PortalAuthService {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthService.class);
    private static final String GENERIC_EMAIL_RESPONSE =
            "If an account exists for that email, further instructions have been sent.";
    private static final String INVALID_CREDENTIALS = "Incorrect username or password";
    private static final String INVALID_LINK =
            "This link is invalid or has already been used. You can request a new link.";
    private static final String EXPIRED_LINK =
            "This link has expired. You can request a new link.";

    private final KeycloakAdminService keycloakAdminService;
    private final KeycloakTokenService keycloakTokenService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PortalInviteMailer inviteMailer;
    private final OrgAdminInviteClient inviteClient;
    private final ParknovaProperties properties;

    public PortalAuthService(
            KeycloakAdminService keycloakAdminService,
            KeycloakTokenService keycloakTokenService,
            PasswordPolicyValidator passwordPolicyValidator,
            PortalInviteMailer inviteMailer,
            OrgAdminInviteClient inviteClient,
            ParknovaProperties properties
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.keycloakTokenService = keycloakTokenService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.inviteMailer = inviteMailer;
        this.inviteClient = inviteClient;
        this.properties = properties;
    }

    public ProvisionTenantAdminResponse provision(ProvisionTenantAdminRequest request) {
        String email = request.email().trim().toLowerCase();
        String orgName = request.organizationName().trim();
        ProvisionResult result = keycloakAdminService.provisionTenantAdmin(
                email, request.organizationId(), orgName);
        String token = issueAndStoreInvite(
                result.userId(),
                email,
                request.organizationId(),
                orgName,
                KeycloakAdminService.INVITE_KIND_SETUP
        );
        inviteMailer.sendSetupInvite(email, orgName, setupLink(token));
        return new ProvisionTenantAdminResponse(
                "Tenant admin provisioned. Invite email queued.",
                email,
                request.organizationId()
        );
    }

    public SetupPreviewResponse previewSetup(String token) {
        PortalInviteRecord invite = requireUsableInvite(token, null);
        return new SetupPreviewResponse(
                invite.email(),
                invite.organizationName(),
                invite.organizationId(),
                invite.kind()
        );
    }

    public MessageResponse setupPassword(SetupPasswordRequest request) {
        PortalInviteRecord invite = requireUsableInvite(request.token(), KeycloakAdminService.INVITE_KIND_SETUP);
        if (keycloakAdminService.hasPasswordCredential(invite.keycloakUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        }
        passwordPolicyValidator.validateConfirm(request.password(), request.confirmPassword());
        inviteClient.consume(request.token());
        keycloakAdminService.activateWithPassword(invite.keycloakUserId(), request.password());
        return new MessageResponse("Account activated. You can now sign in.");
    }

    public MessageResponse resendInvite(EmailRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<UserRepresentation> userOpt = keycloakAdminService.findByEmail(email);
        if (userOpt.isPresent()) {
            UserRepresentation user = userOpt.get();
            if (keycloakAdminService.hasRealmRole(user.getId(), KeycloakAdminService.ROLE_SYSTEM_ADMIN)
                    && !keycloakAdminService.hasPasswordCredential(user.getId())) {
                String orgName = KeycloakAdminService.firstAttr(user, KeycloakAdminService.ATTR_ORGANIZATION_NAME);
                Long orgId = parseOrgId(user);
                if (orgId != null) {
                    String token = issueAndStoreInvite(
                            user.getId(), email, orgId, orgName, KeycloakAdminService.INVITE_KIND_SETUP);
                    inviteMailer.sendSetupInvite(email, orgName, setupLink(token));
                }
            }
        }
        return new MessageResponse(GENERIC_EMAIL_RESPONSE);
    }

    public MessageResponse forgotPassword(EmailRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<UserRepresentation> userOpt = keycloakAdminService.findByEmail(email);
        if (userOpt.isPresent()) {
            UserRepresentation user = userOpt.get();
            if (keycloakAdminService.hasRealmRole(user.getId(), KeycloakAdminService.ROLE_SYSTEM_ADMIN)
                    && keycloakAdminService.hasPasswordCredential(user.getId())) {
                String orgName = KeycloakAdminService.firstAttr(user, KeycloakAdminService.ATTR_ORGANIZATION_NAME);
                Long orgId = parseOrgId(user);
                if (orgId != null) {
                    String token = issueAndStoreInvite(
                            user.getId(), email, orgId, orgName, KeycloakAdminService.INVITE_KIND_RESET);
                    inviteMailer.sendPasswordReset(email, resetLink(token));
                }
            }
        }
        return new MessageResponse(GENERIC_EMAIL_RESPONSE);
    }

    public MessageResponse resetPassword(SetupPasswordRequest request) {
        PortalInviteRecord invite = requireUsableInvite(request.token(), KeycloakAdminService.INVITE_KIND_RESET);
        passwordPolicyValidator.validateConfirm(request.password(), request.confirmPassword());
        inviteClient.consume(request.token());
        keycloakAdminService.activateWithPassword(invite.keycloakUserId(), request.password());
        keycloakAdminService.logoutOtherSessions(invite.keycloakUserId(), null);
        return new MessageResponse("Password updated. You can now sign in.");
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<UserRepresentation> userOpt = keycloakAdminService.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        UserRepresentation user = userOpt.get();
        if (!keycloakAdminService.hasRealmRole(user.getId(), KeycloakAdminService.ROLE_SYSTEM_ADMIN)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (!keycloakAdminService.hasPasswordCredential(user.getId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        try {
            return keycloakTokenService.login(user.getUsername(), request.password());
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
            }
            throw ex;
        }
    }

    public MessageResponse changePassword(Jwt jwt, ChangePasswordRequest request) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }

        Optional<UserRepresentation> userOpt = keycloakAdminService.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            userOpt = keycloakAdminService.findByUsername(email.trim().toLowerCase());
        }
        if (userOpt.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        UserRepresentation user = userOpt.get();
        if (!keycloakAdminService.hasRealmRole(user.getId(), KeycloakAdminService.ROLE_SYSTEM_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a garage portal administrator");
        }

        passwordPolicyValidator.validateConfirm(request.newPassword(), request.confirmPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must differ from the current password");
        }

        try {
            keycloakTokenService.login(user.getUsername(), request.currentPassword());
        } catch (ApiException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        keycloakAdminService.changePassword(user.getId(), request.newPassword());
        String keepSessionId = jwt.getClaimAsString("sid");
        keycloakAdminService.logoutOtherSessions(user.getId(), keepSessionId);
        log.info("Password changed for tenant admin userId={}", user.getId());
        return new MessageResponse("Your password was changed successfully.");
    }

    private String issueAndStoreInvite(
            String keycloakUserId,
            String email,
            long organizationId,
            String organizationName,
            String kind
    ) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.portal().inviteTtlHours(), ChronoUnit.HOURS);
        inviteClient.create(token, organizationId, organizationName, email, keycloakUserId, kind, expiresAt);
        return token;
    }

    private PortalInviteRecord requireUsableInvite(String token, String expectedKind) {
        PortalInviteRecord invite = inviteClient.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK));
        if (!invite.usable()) {
            if (invite.expired()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, EXPIRED_LINK);
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        }
        if (expectedKind != null && !expectedKind.equalsIgnoreCase(invite.kind())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        }
        return invite;
    }

    private String setupLink(String token) {
        return trimSlash(properties.portal().baseUrl()) + "/setup?token=" + token;
    }

    private String resetLink(String token) {
        return trimSlash(properties.portal().baseUrl()) + "/reset-password?token=" + token;
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static Long parseOrgId(UserRepresentation user) {
        String raw = KeycloakAdminService.firstAttr(user, KeycloakAdminService.ATTR_ORGANIZATION_ID);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
