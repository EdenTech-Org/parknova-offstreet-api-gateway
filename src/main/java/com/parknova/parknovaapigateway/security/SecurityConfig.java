package com.parknova.parknovaapigateway.security;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
        Converter<Jwt, AbstractAuthenticationToken> roles = KeycloakRealmRoleConverter.authenticationConverter();

        JwtAuthenticationProvider consumerProvider = new JwtAuthenticationProvider(jwtDecoder);
        consumerProvider.setJwtAuthenticationConverter(roles);

        JwtAuthenticationProvider enforcerProvider = new JwtAuthenticationProvider(enforcerJwtDecoder);
        enforcerProvider.setJwtAuthenticationConverter(roles);

        AuthenticationManager consumer = new ProviderManager(consumerProvider);
        AuthenticationManager enforcer = new ProviderManager(enforcerProvider);
        return request -> {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/enforcer/")) {
                return enforcer;
            }
            return consumer;
        };
    }

    @Bean
    public OrgAdminTenantFilter orgAdminTenantFilter() {
        return new OrgAdminTenantFilter();
    }

    /**
     * CORS for local gateway controllers ({@code /auth/**}, {@code /internal/auth/**}).
     * Spring Cloud Gateway {@code globalcors} only covers proxied routes — without this,
     * browser calls from the portal FE fail preflight (e.g. custom {@code X-SERVICE-TO-SERVICE}).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${parknova.cors.allowed-origin-patterns:https://zone-parking-fe-dev.eden-tech.io,https://*.eden-tech.io,http://localhost:*}")
            String allowedOriginPatterns
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(splitCsv(allowedOriginPatterns));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> authenticationManagerResolver,
            OrgAdminTenantFilter orgAdminTenantFilter
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(cors -> {
        });
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        );
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PublicEndpoints.getEndpoints()).permitAll()
                .requestMatchers("/org-admin/**").hasRole("SYSTEM_ADMIN")
                .anyRequest().authenticated()
        );
        AuthenticationManagerResolver<HttpServletRequest> resolver = authenticationManagerResolver.getIfAvailable();
        if (resolver != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(resolver));
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));
        }
        http.addFilterAfter(orgAdminTenantFilter, BearerTokenAuthenticationFilter.class);
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        return http.build();
    }
}
