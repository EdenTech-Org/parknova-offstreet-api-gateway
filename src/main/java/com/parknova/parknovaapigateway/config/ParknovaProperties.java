package com.parknova.parknovaapigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parknova")
public record ParknovaProperties(
        Keycloak keycloak,
        Otp otp,
        Mail mail
) {
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

    public record Otp(int length, int ttlMinutes, int maxAttempts) {
    }

    public record Mail(String from) {
    }
}
