
package com.sme.maintenance.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.sme.maintenance.entities.User;

@Service
public class AuthenticatedUserService {

    private final UserService userService;

    public AuthenticatedUserService(UserService userService) {
        this.userService = userService;
    }

    public User getCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required"
            );
        }

        return userService.getOrCreateUser(
            jwt.getSubject(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("preferred_username")
        );
    }
}
