package com.sme.maintenance.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Reusable JWT Authentication Converter.
 * Maps Keycloak realm roles from realm_access.roles to Spring Security authorities (ROLE_*).
 * Note: This package (com.sme.maintenance.security) is self-contained and designed
 * to be copied directly into future microservices when splitting the backend.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = StreamUtils(
                defaultAuthoritiesConverter.convert(jwt),
                extractRealmRoles(jwt)
        );

        String principalClaimValue = JwtClaimsHelper.getUsername(jwt);
        return new JwtAuthenticationToken(jwt, authorities, principalClaimValue);
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List<?> rolesList)) {
            return Collections.emptyList();
        }

        java.util.Set<GrantedAuthority> authorities = new java.util.HashSet<>();
        for (Object role : rolesList) {
            if (role instanceof String roleStr) {
                String trimmed = roleStr.trim();
                if (trimmed.isEmpty()) continue;
                // Add original-case version: e.g. ROLE_admin, ROLE_premium
                authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed.replace("-", "_")));
                // Add normalized uppercase version: e.g. ROLE_ADMIN, ROLE_SUPER_ADMIN, ROLE_PREMIUM
                String normalized = trimmed.toUpperCase(java.util.Locale.ROOT).replace("-", "_");
                authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
                if ("SUPER_ADMIN".equals(normalized) || "SUPERADMIN".equals(normalized)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                }
            }
        }
        return authorities;
    }

    private static Collection<GrantedAuthority> StreamUtils(Collection<GrantedAuthority> c1, Collection<GrantedAuthority> c2) {
        Collection<GrantedAuthority> result = new java.util.HashSet<>();
        if (c1 != null) result.addAll(c1);
        if (c2 != null) result.addAll(c2);
        return result;
    }
}
