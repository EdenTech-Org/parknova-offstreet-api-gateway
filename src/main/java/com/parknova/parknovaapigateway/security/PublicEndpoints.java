package com.parknova.parknovaapigateway.security;

/**
 * Paths that skip JWT validation at the gateway.
 * ANPR gate endpoints are public here and re-validated by {@code X-SERVICE-TO-SERVICE} in mobile-service.
 * Tenant-admin provision is public here and re-validated by {@code X-SERVICE-TO-SERVICE} in the gateway controller.
 * {@code /auth/portal/change-password} requires a Bearer JWT.
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    public static String[] getEndpoints() {
        return new String[]{
                "/auth/register",
                "/auth/login",
                "/auth/refresh",
                "/auth/logout",
                "/auth/portal/login",
                "/auth/portal/setup",
                "/auth/portal/setup-password",
                "/auth/portal/resend-invite",
                "/auth/portal/forgot-password",
                "/auth/portal/reset-password",
                "/internal/auth/**",
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
