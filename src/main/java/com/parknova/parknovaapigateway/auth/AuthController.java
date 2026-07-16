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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

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
        return authService.login(request);
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
        return authService.logout(request);
    }
}
