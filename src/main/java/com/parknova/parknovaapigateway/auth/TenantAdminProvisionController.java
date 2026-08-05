package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminRequest;
import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminResponse;
import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth/tenant-admin")
public class TenantAdminProvisionController {

    public static final String SERVICE_HEADER = "X-SERVICE-TO-SERVICE";

    private final PortalAuthService portalAuthService;
    private final ParknovaProperties properties;

    public TenantAdminProvisionController(PortalAuthService portalAuthService, ParknovaProperties properties) {
        this.portalAuthService = portalAuthService;
        this.properties = properties;
    }

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionTenantAdminResponse provision(
            @RequestHeader(value = SERVICE_HEADER, required = false) String serviceSecret,
            @Valid @RequestBody ProvisionTenantAdminRequest request
    ) {
        requireServiceSecret(serviceSecret);
        return portalAuthService.provision(request);
    }

    private void requireServiceSecret(String serviceSecret) {
        String expected = properties.serviceToServiceSecret();
        if (serviceSecret == null || serviceSecret.isBlank() || expected == null || !expected.equals(serviceSecret)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing or invalid X-SERVICE-TO-SERVICE header");
        }
    }
}
