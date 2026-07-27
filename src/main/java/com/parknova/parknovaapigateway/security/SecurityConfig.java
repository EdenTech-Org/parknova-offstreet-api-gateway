package com.parknova.parknovaapigateway.security;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean(name = "jwtDecoder")
    @ConditionalOnProperty(name = "parknova.keycloak.jwt-decoder-enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder(ParknovaProperties properties) {
        String jwkSetUri = properties.keycloak().issuerUri() + "/protocol/openid-connect/certs";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean(name = "enforcerJwtDecoder")
    @ConditionalOnProperty(name = "parknova.keycloak.jwt-decoder-enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder enforcerJwtDecoder(ParknovaProperties properties) {
        ParknovaProperties.EnforcerKeycloak enforcer = properties.enforcerKeycloak();
        String base = enforcer != null && enforcer.baseUrl() != null && !enforcer.baseUrl().isBlank()
                ? enforcer.baseUrl()
                : properties.keycloak().baseUrl();
        String realm = enforcer != null && enforcer.realm() != null && !enforcer.realm().isBlank()
                ? enforcer.realm()
                : "eden-crm-sec-users";
        String jwkSetUri = base + "/realms/" + realm + "/protocol/openid-connect/certs";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    @ConditionalOnProperty(name = "parknova.keycloak.jwt-decoder-enabled", havingValue = "true", matchIfMissing = true)
    public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            @Qualifier("enforcerJwtDecoder") JwtDecoder enforcerJwtDecoder
    ) {
        AuthenticationManager consumer = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
        AuthenticationManager enforcer = new ProviderManager(new JwtAuthenticationProvider(enforcerJwtDecoder));
        return request -> {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/enforcer/")) {
                return enforcer;
            }
            return consumer;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> authenticationManagerResolver
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        );
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PublicEndpoints.getEndpoints()).permitAll()
                .anyRequest().authenticated()
        );
        AuthenticationManagerResolver<HttpServletRequest> resolver = authenticationManagerResolver.getIfAvailable();
        if (resolver != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(resolver));
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));
        }
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        return http.build();
    }
}
