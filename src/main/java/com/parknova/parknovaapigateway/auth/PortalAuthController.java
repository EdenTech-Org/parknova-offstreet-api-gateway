package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.ChangePasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.EmailRequest;
import com.parknova.parknovaapigateway.auth.dto.LoginRequest;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.SetupPasswordRequest;
import com.parknova.parknovaapigateway.auth.dto.SetupPreviewResponse;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/portal")
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    @GetMapping("/setup")
    public SetupPreviewResponse setupPreview(@RequestParam("token") String token) {
        return portalAuthService.previewSetup(token);
    }

    @PostMapping("/setup-password")
    public MessageResponse setupPassword(@Valid @RequestBody SetupPasswordRequest request) {
        return portalAuthService.setupPassword(request);
    }

    @PostMapping("/resend-invite")
    public MessageResponse resendInvite(@Valid @RequestBody EmailRequest request) {
        return portalAuthService.resendInvite(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        return portalAuthService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody SetupPasswordRequest request) {
        return portalAuthService.resetPassword(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return portalAuthService.login(request);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return portalAuthService.changePassword(jwt, request);
    }
}
