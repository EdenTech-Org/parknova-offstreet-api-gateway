package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.LogoutRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.RefreshRequest;
import com.parknova.parknovaapigateway.auth.dto.RegisterRequest;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakAdminService;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakTokenService;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final KeycloakAdminService keycloakAdminService;
    private final KeycloakTokenService keycloakTokenService;

    public AuthService(
            KeycloakAdminService keycloakAdminService,
            KeycloakTokenService keycloakTokenService
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.keycloakTokenService = keycloakTokenService;
    }

    public MessageResponse register(RegisterRequest request) {
        keycloakAdminService.createUser(
                request.username().trim(),
                request.email().trim().toLowerCase(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.phone().trim(),
                request.password()
        );
        return new MessageResponse("Registration successful. You can now log in.");
    }

    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        var user = keycloakAdminService.findByUsername(username);
        if (user.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return keycloakTokenService.login(user.get().getUsername(), request.password());
    }

    public TokenResponse refresh(RefreshRequest request) {
        return keycloakTokenService.refresh(request.refreshToken());
    }

    public MessageResponse logout(LogoutRequest request) {
        keycloakTokenService.logout(request.refreshToken());
        return new MessageResponse("Logged out successfully. Active sessions have been revoked.");
    }
}
