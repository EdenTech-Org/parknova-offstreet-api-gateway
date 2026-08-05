package com.parknova.parknovaapigateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrgAdminTenantFilterTest {

    private final OrgAdminTenantFilter filter = new OrgAdminTenantFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectsOrganizationIdWhenMissing() throws Exception {
        authenticate("42", true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/org-admin/api/v1/dashboard/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            jakarta.servlet.http.HttpServletRequest http = (jakarta.servlet.http.HttpServletRequest) req;
            assertThat(http.getParameter("organizationId")).isEqualTo("42");
            assertThat(http.getQueryString()).contains("organizationId=42");
        });

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMismatchedOrganizationId() throws Exception {
        authenticate("42", true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/org-admin/api/v1/dashboard/summary");
        request.setParameter("organizationId", "99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (FilterChain) (req, res) -> {
            throw new AssertionError("should not proceed");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void rejectsMissingSystemAdminRole() throws Exception {
        authenticate("42", false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/org-admin/api/v1/alerts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("should not proceed");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void skipsNonOrgAdminPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/organizations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    private static void authenticate(String organizationId, boolean systemAdmin) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("organizationId", organizationId)
                .claim("email", "admin@acme.example")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        List<SimpleGrantedAuthority> authorities = systemAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))
                : List.of();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
