package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.LogoutRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.RegisterRequest;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakAdminService;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakTokenService;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private KeycloakTokenService keycloakTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_passesTrimmedFieldsIncludingNamesToKeycloak() {
        RegisterRequest request = new RegisterRequest(
                "  jdoe  ",
                "  John.Doe@Example.COM  ",
                "  John  ",
                "  Doe  ",
                "  +966500000000  ",
                "SecurePass1!"
        );

        when(keycloakAdminService.createUser(
                "jdoe",
                "john.doe@example.com",
                "John",
                "Doe",
                "+966500000000",
                "SecurePass1!"
        )).thenReturn("user-id-1");

        MessageResponse response = authService.register(request);

        assertThat(response.message()).contains("Registration successful");
        verify(keycloakAdminService).createUser(
                "jdoe",
                "john.doe@example.com",
                "John",
                "Doe",
                "+966500000000",
                "SecurePass1!"
        );
    }

    @Test
    void login_resolvesEmailToUsernameAndReturnsTokens() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("jdoe");
        user.setEmail("jdoe@example.com");
        user.setEmailVerified(false);

        when(keycloakAdminService.findByEmail("jdoe@example.com")).thenReturn(Optional.of(user));
        when(keycloakTokenService.login("jdoe", "SecurePass1!"))
                .thenReturn(new TokenResponse("access", "refresh", 300L, 1800L, "Bearer", "openid"));

        TokenResponse tokens = authService.login(new LoginRequest("  JDOE@EXAMPLE.COM  ", "SecurePass1!"));

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
        verify(keycloakTokenService).login(eq("jdoe"), eq("SecurePass1!"));
    }

    @Test
    void login_whenUserMissing_throwsUnauthorized() {
        when(keycloakAdminService.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "bad")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void logout_delegatesRefreshTokenToKeycloak() {
        MessageResponse response = authService.logout(new LogoutRequest("refresh-token-xyz"));

        assertThat(response.message()).contains("Logged out successfully");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(keycloakTokenService).logout(captor.capture());
        assertThat(captor.getValue()).isEqualTo("refresh-token-xyz");
    }
}
