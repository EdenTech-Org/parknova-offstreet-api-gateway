package com.parknova.parknovaapigateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For {@code /org-admin/**}: require JWT {@code organizationId}, reject cross-tenant
 * {@code organizationId} query values, and inject the claim as a query param when missing
 * so org-admin controllers keep working unchanged.
 */
public class OrgAdminTenantFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/org-admin/";
    public static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }
        if (authentication.getAuthorities().stream().noneMatch(a -> ROLE_SYSTEM_ADMIN.equals(a.getAuthority()))) {
            writeError(response, HttpStatus.FORBIDDEN, "SYSTEM_ADMIN role required");
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String tokenOrgId = claimAsString(jwt, "organizationId");
        if (tokenOrgId == null || tokenOrgId.isBlank()) {
            writeError(response, HttpStatus.FORBIDDEN, "Token is missing organizationId claim");
            return;
        }

        String queryOrgId = request.getParameter("organizationId");
        if (queryOrgId != null && !queryOrgId.isBlank() && !tokenOrgId.equals(queryOrgId.trim())) {
            writeError(response, HttpStatus.FORBIDDEN, "organizationId does not match token");
            return;
        }

        HttpServletRequest toForward = request;
        if (queryOrgId == null || queryOrgId.isBlank()) {
            toForward = new OrganizationIdInjectingRequest(request, tokenOrgId);
        }
        filterChain.doFilter(toForward, response);
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
                        + "\",\"message\":\"" + message + "\"}");
    }

    private static String claimAsString(Jwt jwt, String name) {
        String asString = jwt.getClaimAsString(name);
        if (asString != null && !asString.isBlank()) {
            return asString;
        }
        Object raw = jwt.getClaim(name);
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        return String.valueOf(raw);
    }

    static final class OrganizationIdInjectingRequest extends HttpServletRequestWrapper {
        private final String organizationId;

        OrganizationIdInjectingRequest(HttpServletRequest request, String organizationId) {
            super(request);
            this.organizationId = organizationId;
        }

        @Override
        public String getParameter(String name) {
            if ("organizationId".equals(name)) {
                String existing = super.getParameter(name);
                if (existing == null || existing.isBlank()) {
                    return organizationId;
                }
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            if ("organizationId".equals(name)) {
                String[] existing = super.getParameterValues(name);
                if (existing == null || existing.length == 0
                        || existing[0] == null || existing[0].isBlank()) {
                    return new String[]{organizationId};
                }
            }
            return super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> map = new HashMap<>(super.getParameterMap());
            String[] existing = map.get("organizationId");
            if (existing == null || existing.length == 0
                    || existing[0] == null || existing[0].isBlank()) {
                map.put("organizationId", new String[]{organizationId});
            }
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getParameterNames();
            while (original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            names.add("organizationId");
            return Collections.enumeration(names);
        }

        @Override
        public String getQueryString() {
            String qs = super.getQueryString();
            if (qs == null || qs.isBlank()) {
                return "organizationId=" + organizationId;
            }
            if (qs.contains("organizationId=")) {
                return qs;
            }
            return qs + "&organizationId=" + organizationId;
        }
    }
}
