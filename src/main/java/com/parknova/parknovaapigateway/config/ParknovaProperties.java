package com.parknova.parknovaapigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

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
            Boolean jwtDecoderEnabled,
            /** Per-channel login (ROPC) clients: portal / org / mobile. */
            Map<String, ClientCredentials> clients
    ) {
        /**
         * Resolves the confidential login client for a channel, falling back to the top-level
         * {@code clientId}/{@code clientSecret} (the gateway/admin client) when no per-channel
         * client is configured — keeps single-client deployments working.
         */
        public ClientCredentials clientFor(String channel) {
            if (clients != null && channel != null && clients.containsKey(channel)) {
                return clients.get(channel);
            }
            return new ClientCredentials(clientId, clientSecret);
        }

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

    public record ClientCredentials(String clientId, String clientSecret) {
    }

    public record Otp(int length, int ttlMinutes, int maxAttempts) {
    }

    public record Mail(String from) {
    }
}
