package com.parknova.parknovaapigateway.config;

import com.parknova.parknovaapigateway.config.ParknovaProperties.Keycloak;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ParknovaProperties.class)
public class KeycloakConfig {

    @Bean
    public org.keycloak.admin.client.Keycloak keycloakAdmin(ParknovaProperties properties) {
        Keycloak kc = properties.keycloak();
        return KeycloakBuilder.builder()
                .serverUrl(kc.baseUrl())
                .realm(kc.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(kc.clientId())
                .clientSecret(kc.clientSecret())
                .build();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
