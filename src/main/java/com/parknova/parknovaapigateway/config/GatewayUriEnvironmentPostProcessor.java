package com.parknova.parknovaapigateway.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Gateway MVC NPEs when a route {@code uri} has a null scheme (empty ConfigMap value
 * or {@code host:port} without {@code http://}):
 * {@code Cannot invoke String.toLowerCase() because "scheme" is null}.
 *
 * <p>Also causes {@code 405 Method Not Allowed} on {@code POST /client/**} when routes
 * fail to register and Tomcat's static resource handler rejects POST.</p>
 *
 * <p>Also allowlists {@code Authorization} for the JDK HttpClient used by Gateway MVC
 * (otherwise Bearer is dropped on the way to mobile-service).</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayUriEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger LOG = Logger.getLogger(GatewayUriEnvironmentPostProcessor.class.getName());

    private static final String JDK_ALLOW_RESTRICTED_HEADERS = "jdk.httpclient.allowRestrictedHeaders";

    static final String MOBILE = "MOBILE_SERVICE_URI";
    static final String OFFSTREET = "OFFSTREET_SERVICE_URI";
    static final String DEFAULT_MOBILE = "http://localhost:8081";
    static final String DEFAULT_OFFSTREET = "http://localhost:8090";

    /** Direct Spring property overrides sometimes set in K8s without scheme. */
    static final String ROUTE0_URI = "spring.cloud.gateway.mvc.routes[0].uri";
    static final String ROUTE1_URI = "spring.cloud.gateway.mvc.routes[1].uri";
    static final String ROUTE0_URI_ENV = "SPRING_CLOUD_GATEWAY_MVC_ROUTES_0_URI";
    static final String ROUTE1_URI_ENV = "SPRING_CLOUD_GATEWAY_MVC_ROUTES_1_URI";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        allowJdkAuthorizationHeader();

        String mobile = normalize(firstNonBlank(environment, MOBILE, ROUTE0_URI, ROUTE0_URI_ENV), DEFAULT_MOBILE);
        String offstreet = normalize(
                firstNonBlank(environment, OFFSTREET, ROUTE1_URI, ROUTE1_URI_ENV), DEFAULT_OFFSTREET);

        Map<String, Object> fixes = new LinkedHashMap<>();
        // Env keys used by application.yml placeholders
        fixes.put(MOBILE, mobile);
        fixes.put(OFFSTREET, offstreet);
        // Force concrete route URIs so empty / scheme-less overrides cannot crash Gateway MVC
        fixes.put(ROUTE0_URI, mobile);
        fixes.put(ROUTE1_URI, offstreet);
        fixes.put(ROUTE0_URI_ENV, mobile);
        fixes.put(ROUTE1_URI_ENV, offstreet);

        environment.getPropertySources().addFirst(new MapPropertySource("gatewayUriDefaults", fixes));
        LOG.info(() -> "Gateway route URIs normalized: mobile=" + mobile + " offstreet=" + offstreet
                + " jdk.allowRestrictedHeaders=" + System.getProperty(JDK_ALLOW_RESTRICTED_HEADERS));
    }

    static void allowJdkAuthorizationHeader() {
        String current = System.getProperty(JDK_ALLOW_RESTRICTED_HEADERS);
        if (current == null || current.isBlank()) {
            System.setProperty(JDK_ALLOW_RESTRICTED_HEADERS, "authorization,host");
            return;
        }
        if (!current.toLowerCase().contains("authorization")) {
            System.setProperty(JDK_ALLOW_RESTRICTED_HEADERS, current + ",authorization");
        }
    }

    private static String firstNonBlank(ConfigurableEnvironment env, String... keys) {
        for (String key : keys) {
            String value = env.getProperty(key);
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
                return value;
            }
        }
        return null;
    }

    static String normalize(String raw, String defaultUri) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            return defaultUri;
        }
        String value = raw.trim().replaceAll("^\"|\"$", "");
        if (value.isBlank() || "null".equalsIgnoreCase(value)) {
            return defaultUri;
        }
        // host:port or //host:port → http://host:port
        if (value.startsWith("//")) {
            return "http:" + value;
        }
        if (!value.contains("://")) {
            return "http://" + value;
        }
        return value;
    }
}
