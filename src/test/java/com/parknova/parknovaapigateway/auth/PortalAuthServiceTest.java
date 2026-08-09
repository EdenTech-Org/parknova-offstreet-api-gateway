package com.parknova.parknovaapigateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parknova.parknovaapigateway.auth.dto.ChangePasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.EmailRequest;
import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalAuthServiceTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;
    @Mock
    private KeycloakTokenService keycloakTokenService;
    @Mock
    private PortalInviteMailer inviteMailer;
    @Mock
    private OrgAdminInviteClient inviteClient;

    private PortalAuthService portalAuthService;

    @BeforeEach
    void setUp() {
        ParknovaProperties properties = new ParknovaProperties(
                new ParknovaProperties.Keycloak("http://localhost", "parknova", "client", "secret", true),
                null,
                new ParknovaProperties.Portal("http://localhost:3000", 72),
                new ParknovaProperties.Mail(false, "noreply@test"),
                "test-secret",
                "http://localhost:8091"
        );
        portalAuthService = new PortalAuthService(
                keycloakAdminService,
                keycloakTokenService,
                new PasswordPolicyValidator(),
                inviteMailer,
                inviteClient,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void provision_createsInviteInOrgAdminAndSendsEmail() {
        when(keycloakAdminService.provisionTenantAdmin("acme-admin", "admin@acme.example", 42L))
                .thenReturn(new ProvisionResult("user-1", true));
        when(inviteClient.create(anyString(), eq(42L), eq("admin@acme.example"),
                eq("user-1"), eq("SETUP"), any(Instant.class)))
                .thenAnswer(inv -> usableInvite(inv.getArgument(0), "SETUP"));

        var response = portalAuthService.provision(
                new ProvisionTenantAdminRequest(42L, "acme-admin", "Admin@Acme.Example"));

        assertThat(response.email()).isEqualTo("admin@acme.example");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(inviteClient).create(tokenCaptor.capture(), eq(42L), eq("admin@acme.example"),
                eq("user-1"), eq("SETUP"), any(Instant.class));
        verify(inviteMailer).sendSetupInvite(
                eq("acme-admin"),
                eq("admin@acme.example"),
                eq("http://localhost:3000/setup?token=" + tokenCaptor.getValue()));
    }

    @Test
    void previewSetup_returnsEmailWhenTokenValid() {
        when(inviteClient.findByToken("tok"))
                .thenReturn(Optional.of(usableInvite("tok", "SETUP")));

        SetupPreviewResponse preview = portalAuthService.previewSetup("tok");

        assertThat(preview.email()).isEqualTo("admin@acme.example");
        assertThat(preview.organizationId()).isEqualTo(42L);
        assertThat(preview.kind()).isEqualTo("SETUP");
    }

    @Test
    void previewSetup_whenExpired_throwsClearMessage() {
        when(inviteClient.findByToken("old"))
                .thenReturn(Optional.of(new PortalInviteRecord(
                        "old", 42L, "", "admin@acme.example", "u1", "SETUP",
                        Instant.now().minusSeconds(10), null, null, true, false)));

        assertThatThrownBy(() -> portalAuthService.previewSetup("old"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("expired");
                });
    }

    @Test
    void setupPassword_consumesInviteAndActivatesAccount() {
        when(inviteClient.findByToken("tok"))
                .thenReturn(Optional.of(usableInvite("tok", "SETUP")));
        when(keycloakAdminService.hasPasswordCredential("u1")).thenReturn(false);
        when(inviteClient.consume("tok")).thenReturn(usableInvite("tok", "SETUP"));

        MessageResponse response = portalAuthService.setupPassword(
                new SetupPasswordRequest("tok", "SecurePass1!", "SecurePass1!"));

        assertThat(response.message()).contains("Account activated");
        verify(inviteClient).consume("tok");
        verify(keycloakAdminService).activateWithPassword("u1", "SecurePass1!");
    }

    @Test
    void login_requiresSystemAdminAndReturnsTokens() {
        UserRepresentation user = new UserRepresentation();
        user.setId("u1");
        user.setUsername("admin@acme.example");
        user.setEmail("admin@acme.example");
        user.setAttributes(Map.of("organizationId", List.of("42")));

        String access = fakeJwt(Map.of("organizationId", "42", "email", "admin@acme.example"));

        when(keycloakAdminService.findByUsername("admin@acme.example")).thenReturn(Optional.of(user));
        when(keycloakAdminService.hasRealmRole("u1", "SYSTEM_ADMIN")).thenReturn(true);
        when(keycloakAdminService.hasPasswordCredential("u1")).thenReturn(true);
        when(keycloakTokenService.login("admin@acme.example", "SecurePass1!"))
                .thenReturn(new TokenResponse(access, "refresh", 300L, 1800L, "Bearer", "openid"));

        TokenResponse tokens = portalAuthService.login(
                new LoginRequest("admin@acme.example", "SecurePass1!"));

        assertThat(tokens.accessToken()).isEqualTo(access);
    }

    @Test
    void login_whenTokenMissingOrganizationId_failsWithConfigHint() {
        UserRepresentation user = new UserRepresentation();
        user.setId("u1");
        user.setUsername("admin@acme.example");
        user.setEmail("admin@acme.example");
        user.setAttributes(Map.of("organizationId", List.of("42")));

        String access = fakeJwt(Map.of("email", "admin@acme.example"));

        when(keycloakAdminService.findByUsername("admin@acme.example")).thenReturn(Optional.of(user));
        when(keycloakAdminService.hasRealmRole("u1", "SYSTEM_ADMIN")).thenReturn(true);
        when(keycloakAdminService.hasPasswordCredential("u1")).thenReturn(true);
        when(keycloakTokenService.login("admin@acme.example", "SecurePass1!"))
                .thenReturn(new TokenResponse(access, "refresh", 300L, 1800L, "Bearer", "openid"));

        assertThatThrownBy(() -> portalAuthService.login(
                new LoginRequest("admin@acme.example", "SecurePass1!")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.getMessage()).contains("organizationId claim");
                });
    }

    @Test
    void login_whenNotSystemAdmin_returnsGenericError() {
        UserRepresentation user = new UserRepresentation();
        user.setId("u1");
        user.setUsername("consumer@example.com");

        when(keycloakAdminService.findByUsername("consumer@example.com")).thenReturn(Optional.of(user));
        when(keycloakAdminService.hasRealmRole("u1", "SYSTEM_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> portalAuthService.login(
                new LoginRequest("consumer@example.com", "SecurePass1!")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getMessage())
                        .isEqualTo("Incorrect username or password"));
        verify(keycloakTokenService, never()).login(anyString(), anyString());
    }

    @Test
    void changePassword_verifiesCurrentAndKeepsSession() {
        UserRepresentation user = new UserRepresentation();
        user.setId("u1");
        user.setUsername("admin@acme.example");
        user.setEmail("admin@acme.example");

        when(keycloakAdminService.findByUsername("admin@acme.example")).thenReturn(Optional.of(user));
        when(keycloakAdminService.hasRealmRole("u1", "SYSTEM_ADMIN")).thenReturn(true);
        when(keycloakTokenService.login("admin@acme.example", "OldPass1!"))
                .thenReturn(new TokenResponse("a", "r", 1L, 1L, "Bearer", "openid"));

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("u1")
                .claim("preferred_username", "admin@acme.example")
                .claim("sid", "session-keep")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        MessageResponse response = portalAuthService.changePassword(
                jwt, new ChangePasswordRequest("OldPass1!", "NewPass2!", "NewPass2!"));

        assertThat(response.message()).contains("password was changed");
        verify(keycloakAdminService).changePassword("u1", "NewPass2!");
        verify(keycloakAdminService).logoutOtherSessions("u1", "session-keep");
    }

    @Test
    void resendInvite_alwaysReturnsGenericMessage() {
        when(keycloakAdminService.findByUsername("missing-user")).thenReturn(Optional.empty());

        MessageResponse response = portalAuthService.resendInvite(new EmailRequest("missing-user"));

        assertThat(response.message()).contains("If an account exists");
        verify(inviteClient, never()).create(anyString(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    private static PortalInviteRecord usableInvite(String token, String kind) {
        return new PortalInviteRecord(
                token, 42L, "", "admin@acme.example", "u1", kind,
                Instant.now().plusSeconds(3600), null, null, false, true);
    }

    private static String fakeJwt(Map<String, Object> claims) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mapper.writeValueAsBytes(claims));
            return header + "." + payload + ".sig";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
