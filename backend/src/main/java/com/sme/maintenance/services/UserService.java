package com.sme.maintenance.services;

import java.sql.Date;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getOrCreateUser(String keycloakId, String email, String username) {
        if (keycloakId == null || keycloakId.isBlank()
                || email == null || email.isBlank()
                || username == null || username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Keycloak subject, email, and username are required"
            );
        }

        String normalizedKeycloakId = keycloakId.trim();
        String normalizedEmail = email.trim();
        String normalizedUsername = username.trim();

        User user = userRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseGet(User::new);
        user.setKeycloakId(normalizedKeycloakId);
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedUsername);
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Date.valueOf(LocalDate.now()));
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserFromKeycloakId(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak subject is required");
        }
        return userRepository.findByKeycloakId(keycloakId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }
}