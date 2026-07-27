package com.parknova.parknovaapigateway.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardJwtHeadersFilterTest {

    private final ForwardJwtHeadersFilter filter = new ForwardJwtHeadersFilter();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addsBearerAndIdentityHeadersFromSecurityContext() {
        Jwt jwt = Jwt.withTokenValue("access-token-value")
                .header("alg", "none")
                .subject("user-sub-1")
                .claim("preferred_username", "jdoe")
                .claim("email", "jdoe@example.com")
                .claim("phone_number", "+966500000000")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/client/v1/me");
        ServerRequest serverRequest = ServerRequest.create(servletRequest, List.of());

        HttpHeaders out = filter.apply(new HttpHeaders(), serverRequest);

        assertThat(out.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token-value");
        assertThat(out.getFirst("X-User-Sub")).isEqualTo("user-sub-1");
        assertThat(out.getFirst("X-User-Username")).isEqualTo("jdoe");
        assertThat(out.getFirst("X-User-Email")).isEqualTo("jdoe@example.com");
        assertThat(out.getFirst("X-User-Phone")).isEqualTo("+966500000000");
    }

    @Test
    void leavesHeadersAloneWhenAnonymous() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/internal/v1/sessions/check-in");
        ServerRequest serverRequest = ServerRequest.create(servletRequest, List.of());

        HttpHeaders in = new HttpHeaders();
        in.add("X-SERVICE-TO-SERVICE", "secret");
        HttpHeaders out = filter.apply(in, serverRequest);

        assertThat(out.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(out.getFirst("X-SERVICE-TO-SERVICE")).isEqualTo("secret");
        assertThat(out.getFirst("X-User-Sub")).isNull();
    }
}
