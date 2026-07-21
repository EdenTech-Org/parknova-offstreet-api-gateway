package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.ForgotPasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.LogoutRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.RegisterRequest;
import com.parknova.parknovaapigateway.auth.dto.ResendOtpRequest;
import com.parknova.parknovaapigateway.auth.dto.ResetPasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.auth.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /** Login channels that map to a per-channel Keycloak client. */
    private static final Set<String> CHANNELS = Set.of("portal", "org", "mobile");
    /** Default channel for the legacy {@code /auth/login} and {@code /auth/logout} paths. */
    private static final String DEFAULT_CHANNEL = "mobile";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-otp")
    public MessageResponse resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return authService.resendOtp(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(DEFAULT_CHANNEL, request);
    }

    /** Per-channel login: {@code /auth/portal/login}, {@code /auth/org/login}, {@code /auth/mobile/login}. */
    @PostMapping("/{channel}/login")
    public TokenResponse login(@PathVariable String channel, @Valid @RequestBody LoginRequest request) {
        return authService.login(requireChannel(channel), request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody LogoutRequest request) {
        return authService.logout(DEFAULT_CHANNEL, request);
    }

    @PostMapping("/{channel}/logout")
    public MessageResponse logout(@PathVariable String channel, @Valid @RequestBody LogoutRequest request) {
        return authService.logout(requireChannel(channel), request);
    }

    private static String requireChannel(String channel) {
        if (channel == null || !CHANNELS.contains(channel)) {
            throw new com.parknova.parknovaapigateway.exception.ApiException(
                    HttpStatus.NOT_FOUND, "Unknown login channel: " + channel);
        }
        return channel;
    }
}
