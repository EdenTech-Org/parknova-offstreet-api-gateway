package com.parknova.parknovaapigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parknova")
public record ParknovaProperties(
        Keycloak keycloak,
        EnforcerKeycloak enforcerKeycloak
) {
    public ParknovaProperties {
        if (enforcerKeycloak == null) {
            enforcerKeycloak = new EnforcerKeycloak(
                    keycloak != null ? keycloak.baseUrl() : "https://keycloak-dev.eden-tech.io",
                    "eden-crm-sec-users"
            );
        }
    }

    public record Keycloak(
            String baseUrl,
            String realm,
            String clientId,
            String clientSecret,
            Boolean jwtDecoderEnabled
    ) {
        public String issuerUri() {
            return baseUrl + "/realms/" + realm;
        }

        public String tokenUri() {
            return issuerUri() + "/protocol/openid-connect/token";
        }

        public String logoutUri() {
            return issuerUri() + "/protocol/openid-connect/logout";
        }

        public String revokeUri() {
            return issuerUri() + "/protocol/openid-connect/revoke";
        }
    }

    public record EnforcerKeycloak(
            String baseUrl,
            String realm
    ) {
        public String issuerUri() {
            String base = baseUrl != null && !baseUrl.isBlank() ? baseUrl : null;
            return (base != null ? base : "") + "/realms/" + realm;
        }
    }
}
