package com.parknova.parknovaapigateway.config;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Inter-service identity bridge: gateway → mobile / enforcer.
 *
 * <p>Mobile does not re-validate JWT signatures; it reads either
 * {@code Authorization: Bearer} or gateway-injected {@code X-User-*} /
 * {@code X-Officer-*} headers. This filter always injects the identity headers
 * from the already-validated JWT so the downstream call works even when the
 * proxy HTTP client strips {@code Authorization}.</p>
 */
@Component
public class ForwardJwtHeadersFilter implements RequestHttpHeadersFilter, Ordered {

    public static final String GATEWAY_MARK = "X-Parknova-Gateway";
    public static final String USER_SUB = "X-User-Sub";
    public static final String USER_USERNAME = "X-User-Username";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_PHONE = "X-User-Phone";
    public static final String OFFICER_SUB = "X-Officer-Sub";
    public static final String OFFICER_USERNAME = "X-Officer-Username";
    public static final String OFFICER_EMAIL = "X-Officer-Email";

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(input);

        String inboundAuth = request.servletRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (inboundAuth != null && !inboundAuth.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, inboundAuth);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return headers;
        }

        Jwt jwt = jwtAuth.getToken();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
        headers.set(GATEWAY_MARK, "1");

        String path = request.servletRequest().getRequestURI();
        boolean enforcer = path != null && path.startsWith("/enforcer/");
        putIdentity(headers, jwt, enforcer);
        return headers;
    }

    private static void putIdentity(HttpHeaders headers, Jwt jwt, boolean enforcer) {
        String sub = jwt.getSubject();
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("username"));
        String email = jwt.getClaimAsString("email");
        String phone = firstNonBlank(jwt.getClaimAsString("phone"), jwt.getClaimAsString("phone_number"));

        if (enforcer) {
            if (sub != null && !sub.isBlank()) {
                headers.set(OFFICER_SUB, sub);
            }
            if (username != null) {
                headers.set(OFFICER_USERNAME, username);
            }
            if (email != null && !email.isBlank()) {
                headers.set(OFFICER_EMAIL, email);
            }
            return;
        }

        if (sub != null && !sub.isBlank()) {
            headers.set(USER_SUB, sub);
        }
        if (username != null) {
            headers.set(USER_USERNAME, username);
        }
        if (email != null && !email.isBlank()) {
            headers.set(USER_EMAIL, email);
        }
        if (phone != null) {
            headers.set(USER_PHONE, phone);
        }
    }

    @Override
    public int getOrder() {
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
