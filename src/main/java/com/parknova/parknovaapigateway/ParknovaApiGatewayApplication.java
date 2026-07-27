package com.parknova.parknovaapigateway;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.TimeZone;

@SpringBootApplication
public class ParknovaApiGatewayApplication {

    private static final String JDK_ALLOW_RESTRICTED_HEADERS = "jdk.httpclient.allowRestrictedHeaders";

    public static void main(String[] args) {
        // Gateway MVC uses JDK HttpClient by default; it drops Authorization unless allowlisted.
        // Without this, /client/v1/me|vehicles fail on servers while lookups still work.
        allowJdkAuthorizationHeader();
        SpringApplication.run(ParknovaApiGatewayApplication.class, args);
    }

    static void allowJdkAuthorizationHeader() {
        String current = System.getProperty(JDK_ALLOW_RESTRICTED_HEADERS);
        if (!StringUtils.hasText(current)) {
            System.setProperty(JDK_ALLOW_RESTRICTED_HEADERS, "authorization,host");
            return;
        }
        if (!current.toLowerCase(Locale.ROOT).contains("authorization")) {
            System.setProperty(JDK_ALLOW_RESTRICTED_HEADERS, current + ",authorization");
        }
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
