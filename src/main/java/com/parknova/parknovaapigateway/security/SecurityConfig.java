package com.parknova.parknovaapigateway.security;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Uses JWKS URI (lazy fetch on first request) instead of issuer discovery at startup.
     * Disabled in tests via {@code parknova.keycloak.jwt-decoder-enabled=false}.
     */
    @Bean
    @ConditionalOnProperty(name = "parknova.keycloak.jwt-decoder-enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder(ParknovaProperties properties) {
        String jwkSetUri = properties.keycloak().issuerUri() + "/protocol/openid-connect/certs";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        );
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PublicEndpoints.getEndpoints()).permitAll()
                .anyRequest().authenticated()
        );
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
        }));
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        return http.build();
    }
}
