package com.parknova.parknovaapigateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdkAuthorizationHeaderAllowlistTest {

    private static final String PROP = "jdk.httpclient.allowRestrictedHeaders";

    @AfterEach
    void clear() {
        System.clearProperty(PROP);
    }

    @Test
    void setsAuthorizationWhenUnset() {
        System.clearProperty(PROP);
        ParknovaApiGatewayApplication.allowJdkAuthorizationHeader();
        assertThat(System.getProperty(PROP).toLowerCase()).contains("authorization");
    }

    @Test
    void appendsAuthorizationWhenOnlyHostIsSet() {
        System.setProperty(PROP, "host");
        ParknovaApiGatewayApplication.allowJdkAuthorizationHeader();
        assertThat(System.getProperty(PROP).toLowerCase()).contains("authorization");
        assertThat(System.getProperty(PROP).toLowerCase()).contains("host");
    }
}
