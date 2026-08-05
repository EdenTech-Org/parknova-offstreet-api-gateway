package com.parknova.parknovaapigateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.ProvisionTenantAdminResponse;
import com.parknova.parknovaapigateway.auth.dto.SetupPreviewResponse;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import com.parknova.parknovaapigateway.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PortalAuthController.class, TenantAdminProvisionController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, PortalAuthControllerTest.TestProps.class})
class PortalAuthControllerTest {

    @TestConfiguration
    static class TestProps {
        @Bean
        ParknovaProperties parknovaProperties() {
            return new ParknovaProperties(
                    new ParknovaProperties.Keycloak("http://localhost", "parknova", "client", "secret", false),
                    null,
                    new ParknovaProperties.Portal("http://localhost:3000", 72),
                    new ParknovaProperties.Mail(false, "noreply@test"),
                    "test-secret",
                    "http://localhost:8091"
            );
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortalAuthService portalAuthService;

    @Test
    void setupPreview_returnsEmail() throws Exception {
        when(portalAuthService.previewSetup("tok"))
                .thenReturn(new SetupPreviewResponse("admin@acme.example", 42L, "SETUP"));

        mockMvc.perform(get("/auth/portal/setup").param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@acme.example"))
                .andExpect(jsonPath("$.organizationId").value(42));
    }

    @Test
    void portalLogin_returnsTokens() throws Exception {
        when(portalAuthService.login(any()))
                .thenReturn(new TokenResponse("access", "refresh", 300L, 1800L, "Bearer", "openid"));

        mockMvc.perform(post("/auth/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@acme.example",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access"));
    }

    @Test
    void setupPassword_whenExpired_returns400() throws Exception {
        when(portalAuthService.setupPassword(any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                        "This link has expired. You can request a new link."));

        mockMvc.perform(post("/auth/portal/setup-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "old",
                                "password", "SecurePass1!",
                                "confirmPassword", "SecurePass1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void provision_requiresServiceHeader() throws Exception {
        mockMvc.perform(post("/internal/auth/tenant-admin/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationId", 42,
                                "email", "admin@acme.example"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provision_withValidSecret_returns201() throws Exception {
        when(portalAuthService.provision(any()))
                .thenReturn(new ProvisionTenantAdminResponse(
                        "Tenant admin provisioned. Invite email queued.",
                        "admin@acme.example",
                        42L));

        mockMvc.perform(post("/internal/auth/tenant-admin/provision")
                        .header("X-SERVICE-TO-SERVICE", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationId", 42,
                                "email", "admin@acme.example"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(42));

        verify(portalAuthService).provision(any());
    }

    @Test
    void resendInvite_returnsGenericMessage() throws Exception {
        when(portalAuthService.resendInvite(any()))
                .thenReturn(new MessageResponse(
                        "If an account exists for that email, further instructions have been sent."));

        mockMvc.perform(post("/auth/portal/resend-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "admin@acme.example"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("If an account exists")));
    }
}
