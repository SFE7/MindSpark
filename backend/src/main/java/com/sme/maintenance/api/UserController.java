package com.sme.maintenance.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.services.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sync")
    public ResponseEntity<UserResponse> syncUser(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");

        var user = userService.getOrCreateUser(keycloakId, email, username);

        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        ));
    }
}
