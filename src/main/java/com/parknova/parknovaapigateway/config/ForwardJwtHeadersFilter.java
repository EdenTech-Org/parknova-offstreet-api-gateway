package com.parknova.parknovaapigateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Mobile-service reads the raw Bearer JWT (and optional {@code X-User-*} headers).
 * Ensure the validated access token is always forwarded on proxied requests —
 * lookups do not need it, but {@code /me}, bootstrap, vehicles, etc. do.
 */
@Component
public class ForwardJwtHeadersFilter implements RequestHttpHeadersFilter, Ordered {

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(input);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return headers;
        }

        Jwt jwt = jwtAuth.getToken();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());

        // Dev/local fallback headers understood by mobile CurrentUserProvider
        if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            headers.set("X-User-Sub", jwt.getSubject());
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            headers.set("X-User-Username", username);
        }
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            headers.set("X-User-Email", email);
        }
        String phone = firstNonBlank(jwt.getClaimAsString("phone"), jwt.getClaimAsString("phone_number"));
        if (phone != null) {
            headers.set("X-User-Phone", phone);
        }
        return headers;
    }

    @Override
    public int getOrder() {
        // After hop-by-hop / content-length filters so we re-assert Authorization last.
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
