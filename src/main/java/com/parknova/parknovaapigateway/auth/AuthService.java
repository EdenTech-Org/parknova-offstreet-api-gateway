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
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakAdminService;
import com.parknova.parknovaapigateway.auth.keycloak.KeycloakTokenService;
import com.parknova.parknovaapigateway.auth.mail.EmailService;
import com.parknova.parknovaapigateway.auth.otp.OtpPurpose;
import com.parknova.parknovaapigateway.auth.otp.OtpService;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakAdminService keycloakAdminService;
    private final KeycloakTokenService keycloakTokenService;
    private final OtpService otpService;
    private final EmailService emailService;

    public AuthService(
            KeycloakAdminService keycloakAdminService,
            KeycloakTokenService keycloakTokenService,
            OtpService otpService,
            EmailService emailService
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.keycloakTokenService = keycloakTokenService;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    public MessageResponse register(RegisterRequest request) {
        keycloakAdminService.createUser(
                request.username().trim(),
                request.email().trim().toLowerCase(),
                request.phone().trim(),
                request.password()
        );
        sendOtp(request.email().trim().toLowerCase(), OtpPurpose.EMAIL_VERIFICATION);
        return new MessageResponse("Registration successful. Please verify your email with the OTP sent.");
    }

    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        String email = request.email().trim().toLowerCase();
        if (keycloakAdminService.findByEmail(email).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        otpService.verify(email, OtpPurpose.EMAIL_VERIFICATION, request.otp());
        keycloakAdminService.markEmailVerified(email);
        return new MessageResponse("Email verified successfully. You can now log in.");
    }

    public MessageResponse resendOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase();
        if (keycloakAdminService.findByEmail(email).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        sendOtp(email, OtpPurpose.EMAIL_VERIFICATION);
        return new MessageResponse("A new verification OTP has been sent to your email.");
    }

    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        var user = keycloakAdminService.findByUsername(username)
                .or(() -> keycloakAdminService.findByEmail(username));
        if (user.isPresent() && !Boolean.TRUE.equals(user.get().isEmailVerified())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Email not verified. Please verify your email first.");
        }
        return keycloakTokenService.login(username, request.password());
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        // Same response whether or not user exists to avoid account enumeration
        if (keycloakAdminService.findByEmail(email).isPresent()) {
            sendOtp(email, OtpPurpose.PASSWORD_RESET);
        } else {
            log.warn("Forgot-password requested for unknown email={}", email);
        }
        return new MessageResponse("If an account exists for this email, a reset OTP has been sent.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        if (keycloakAdminService.findByEmail(email).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        otpService.verify(email, OtpPurpose.PASSWORD_RESET, request.otp());
        keycloakAdminService.resetPassword(email, request.newPassword());
        keycloakAdminService.logoutAllSessions(email);
        return new MessageResponse("Password reset successful. Please log in with your new password.");
    }

    public MessageResponse logout(LogoutRequest request) {
        keycloakTokenService.logout(request.refreshToken());
        return new MessageResponse("Logged out successfully. Active sessions have been revoked.");
    }

    private void sendOtp(String email, OtpPurpose purpose) {
        String code = otpService.generateAndStore(email, purpose);
        try {
            emailService.sendOtp(email, code, purpose);
        } catch (Exception ex) {
            log.warn("SMTP unavailable; OTP for {} ({}): {}", email, purpose, code);
        }
    }
}
