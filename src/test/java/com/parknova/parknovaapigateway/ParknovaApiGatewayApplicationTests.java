package com.parknova.parknovaapigateway;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
class ParknovaApiGatewayApplicationTests {

    @MockBean
    private Keycloak keycloak;

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claims(claims -> claims.putAll(Map.of(
                            "sub", "test-user",
                            "iss", "http://localhost/realms/parknova"
                    )))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }
}
