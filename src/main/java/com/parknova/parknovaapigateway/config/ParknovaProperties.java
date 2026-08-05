package com.parknova.parknovaapigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parknova")
public record ParknovaProperties(
        Keycloak keycloak,
        EnforcerKeycloak enforcerKeycloak,
        Portal portal,
        Mail mail,
        String serviceToServiceSecret,
        String orgAdminBaseUrl
) {
    public ParknovaProperties {
        if (enforcerKeycloak == null) {
            enforcerKeycloak = new EnforcerKeycloak(
                    keycloak != null ? keycloak.baseUrl() : "https://keycloak-dev.eden-tech.io",
                    "eden-crm-sec-users"
            );
        }
        if (portal == null) {
            portal = new Portal("http://localhost:3000", 72);
        }
        if (mail == null) {
            mail = new Mail(false, "noreply@parknova.local");
        }
        if (serviceToServiceSecret == null || serviceToServiceSecret.isBlank()) {
            serviceToServiceSecret = "CHANGE_ME";
        }
        if (orgAdminBaseUrl == null || orgAdminBaseUrl.isBlank()) {
            orgAdminBaseUrl = "http://localhost:8091";
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

    public record Portal(
            String baseUrl,
            Integer inviteTtlHours
    ) {
        public Portal {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:3000";
            }
            if (inviteTtlHours == null || inviteTtlHours <= 0) {
                inviteTtlHours = 72;
            }
        }
    }

    public record Mail(
            Boolean enabled,
            String from
    ) {
        public Mail {
            if (enabled == null) {
                enabled = false;
            }
            if (from == null || from.isBlank()) {
                from = "noreply@parknova.local";
            }
        }
    }
}
