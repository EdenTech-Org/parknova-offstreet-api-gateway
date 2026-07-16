package com.parknova.parknovaapigateway.security;

/**
 * Paths that skip JWT validation at the gateway.
 * ANPR gate endpoints are public here and re-validated by {@code X-SERVICE-TO-SERVICE} in mobile-service.
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    public static String[] getEndpoints() {
        return new String[]{
                "/auth/**",
                "/error",
                "/actuator/health",
                "/actuator/info",
                // Mobile Swagger (optional local debugging through gateway)
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/api-docs/**",
                "/v3/api-docs/**",
                // Gate ANPR → validated by X-SERVICE-TO-SERVICE on the mobile service
                "/internal/v1/sessions/**"
        };
    }
}
