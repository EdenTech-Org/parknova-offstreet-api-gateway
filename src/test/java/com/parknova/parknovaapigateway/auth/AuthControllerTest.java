package com.parknova.parknovaapigateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parknova.parknovaapigateway.auth.dto.MessageResponse;
import com.parknova.parknovaapigateway.auth.dto.RegisterRequest;
import com.parknova.parknovaapigateway.auth.dto.TokenResponse;
import com.parknova.parknovaapigateway.exception.ApiException;
import com.parknova.parknovaapigateway.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_returns201_withValidBody() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new MessageResponse("Registration successful. You can now log in."));

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "jdoe",
                "email", "jdoe@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "phone", "+966500000000",
                "password", "SecurePass1!"
        ));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful. You can now log in."));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_returns400_whenFirstNameMissing() throws Exception {
        String body = """
                {
                  "username": "jdoe",
                  "email": "jdoe@example.com",
                  "lastName": "Doe",
                  "phone": "+966500000000",
                  "password": "SecurePass1!"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields", hasKey("firstName")));
    }

    @Test
    void register_returns400_whenLastNameMissing() throws Exception {
        String body = """
                {
                  "username": "jdoe",
                  "email": "jdoe@example.com",
                  "firstName": "John",
                  "phone": "+966500000000",
                  "password": "SecurePass1!"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", hasKey("lastName")));
    }

    @Test
    void login_returnsTokens() throws Exception {
        when(authService.login(any()))
                .thenReturn(new TokenResponse("access-token", "refresh-token", 300L, 1800L, "Bearer", "openid"));

        String body = """
                {
                  "email": "jdoe@example.com",
                  "password": "SecurePass1!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"));
    }

    @Test
    void login_returns401_whenCredentialsInvalid() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String body = """
                {
                  "email": "jdoe@example.com",
                  "password": "wrong"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void logout_returnsSuccess() throws Exception {
        when(authService.logout(any()))
                .thenReturn(new MessageResponse("Logged out successfully. Active sessions have been revoked."));

        String body = """
                {
                  "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully. Active sessions have been revoked."));
    }
}
