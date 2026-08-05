package com.parknova.parknovaapigateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak {@code realm_access.roles} to Spring {@code ROLE_*} authorities
 * (e.g. {@code SYSTEM_ADMIN} → {@code ROLE_SYSTEM_ADMIN}).
 */
public final class KeycloakRealmRoleConverter {

    private KeycloakRealmRoleConverter() {
    }

    public static Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakRealmRoleConverter::authorities);
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        Collection<GrantedAuthority> scopeAuth = scopes.convert(jwt);
        if (scopeAuth != null) {
            authorities.addAll(scopeAuth);
        }
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> list) {
                for (Object role : list) {
                    if (role != null && !String.valueOf(role).isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
        }
        // Also accept a flat "roles" claim if present
        Object flatRoles = jwt.getClaim("roles");
        if (flatRoles instanceof Collection<?> list) {
            for (Object role : list) {
                if (role != null && !String.valueOf(role).isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
        return List.copyOf(authorities);
    }
}
