package com.parknova.parknovaapigateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUriEnvironmentPostProcessorTest {

    @Test
    void blankFallsBackToDefault() {
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(null, "http://localhost:8081"))
                .isEqualTo("http://localhost:8081");
        assertThat(GatewayUriEnvironmentPostProcessor.normalize("  ", "http://localhost:8081"))
                .isEqualTo("http://localhost:8081");
        assertThat(GatewayUriEnvironmentPostProcessor.normalize("null", "http://localhost:8081"))
                .isEqualTo("http://localhost:8081");
        assertThat(GatewayUriEnvironmentPostProcessor.normalize("\"\"", "http://localhost:8081"))
                .isEqualTo("http://localhost:8081");
    }

    @Test
    void hostPortGetsHttpScheme() {
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(
                "parknova-offstreet-mobile-service:8081", "http://localhost:8081"))
                .isEqualTo("http://parknova-offstreet-mobile-service:8081");
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(
                "//parknova-offstreet-mobile-service:8081", "http://localhost:8081"))
                .isEqualTo("http://parknova-offstreet-mobile-service:8081");
    }

    @Test
    void fullUriUnchanged() {
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(
                "http://parknova-offstreet-mobile-service:8081", "http://localhost:8081"))
                .isEqualTo("http://parknova-offstreet-mobile-service:8081");
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(
                "https://mobile.example.com", "http://localhost:8081"))
                .isEqualTo("https://mobile.example.com");
    }

    @Test
    void stripsSurroundingQuotes() {
        assertThat(GatewayUriEnvironmentPostProcessor.normalize(
                "\"http://mobile:8081\"", "http://localhost:8081"))
                .isEqualTo("http://mobile:8081");
    }
}
