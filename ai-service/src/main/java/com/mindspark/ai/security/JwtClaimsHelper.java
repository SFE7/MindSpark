package com.mindspark.ai.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaimsHelper {

    private JwtClaimsHelper() {}

    public static String getSub(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : null;
    }

    public static String getEmail(Jwt jwt) {
        if (jwt == null) return null;
        String email = jwt.getClaimAsString("email");
        return (email != null && !email.isBlank()) ? email.trim() : null;
    }

    public static String getUsername(Jwt jwt) {
        if (jwt == null) return null;
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername.trim();
        }
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return getSub(jwt);
    }
}
