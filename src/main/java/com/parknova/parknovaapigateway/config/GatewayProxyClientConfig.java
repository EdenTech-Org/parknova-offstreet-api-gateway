package com.parknova.parknovaapigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Gateway MVC defaults to JDK {@code HttpClient}, which silently drops the
 * {@code Authorization} header unless {@code jdk.httpclient.allowRestrictedHeaders}
 * includes it. That breaks the hop to mobile-service ({@code CurrentUserProvider}).
 *
 * <p>Replace with {@link SimpleClientHttpRequestFactory} (HttpURLConnection) so Bearer
 * and {@code X-User-*} / {@code X-Officer-*} headers reach mobile in every environment.
 * Auto-config backs off via {@code @ConditionalOnMissingBean} on
 * {@code gatewayClientHttpRequestFactory}.</p>
 */
@Configuration
public class GatewayProxyClientConfig {

    @Bean
    public ClientHttpRequestFactory gatewayClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }
}
