package com.mindspark.media.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaimsHelper {

    private JwtClaimsHelper() {}

    public static String getUserId(Jwt jwt) {
        if (jwt == null) return null;
        return jwt.getSubject();
    }

    public static String getUsername(Jwt jwt) {
        if (jwt == null) return null;
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return jwt.getSubject();
    }
}
