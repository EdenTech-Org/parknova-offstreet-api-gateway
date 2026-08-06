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

    private final PortalAuthService portalAuthService;

    public TenantAdminProvisionController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionTenantAdminResponse provision(@Valid @RequestBody ProvisionTenantAdminRequest request)
    {
        return portalAuthService.provision(request);
    }


}
