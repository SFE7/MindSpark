package com.mindspark.subscription.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.mindspark.subscription.dto.AdminListResponse;
import com.mindspark.subscription.dto.AdminListResponse.AdminUserSummary;
import com.mindspark.subscription.dto.AdminUserResponse;
import com.mindspark.subscription.dto.CreateAdminRequest;
import com.mindspark.subscription.exception.AdminAlreadyExistsException;
import com.mindspark.subscription.exception.KeycloakAdminNotConfiguredException;
import com.mindspark.subscription.exception.KeycloakRoleNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KeycloakAdminUserService {

    private final RestClient.Builder restClientBuilder;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminUserService(
            RestClient.Builder restClientBuilder,
            @Value("${keycloak.admin.base-url:http://localhost:8180}") String baseUrl,
            @Value("${keycloak.admin.realm:mindspark}") String realm,
            @Value("${keycloak.admin.client-id:}") String clientId,
            @Value("${keycloak.admin.client-secret:}") String clientSecret) {
        this.restClientBuilder = restClientBuilder;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:8180";
        this.realm = realm != null && !realm.isBlank() ? realm : "mindspark";
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public AdminUserResponse createAdminUser(CreateAdminRequest req) {
        if (!isConfigured()) {
            throw new KeycloakAdminNotConfiguredException(
                    "Keycloak Admin client credentials are not configured. See docs/keycloak-admin-roles-setup.md."
            );
        }

        RestClient client = restClientBuilder.build();
        String token = obtainAdminToken(client);

        String email = req.email().trim().toLowerCase();
        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();

        // 1. Create Keycloak user with temporary password
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", req.temporaryPassword(),
                "temporary", true
        );
        Map<String, Object> userPayload = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential)
        );

        String createdUserId = null;
        try {
            ResponseEntity<Void> response = client.post()
                    .uri(baseUrl + "/admin/realms/" + realm + "/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userPayload)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (request, resp) -> {
                        throw new AdminAlreadyExistsException("Admin user with email " + email + " already exists in Keycloak");
                    })
                    .toBodilessEntity();

            URI location = response.getHeaders().getLocation();
            if (location != null) {
                String path = location.getPath();
                createdUserId = path.substring(path.lastIndexOf('/') + 1);
            }
        } catch (AdminAlreadyExistsException e) {
            throw e;
        } catch (HttpClientErrorException.Conflict e) {
            throw new AdminAlreadyExistsException("Admin user with email " + email + " already exists in Keycloak");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new AdminAlreadyExistsException("Admin user with email " + email + " already exists in Keycloak");
            }
            log.error("Failed to create user in Keycloak (HTTP {}): {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak: {}", e.getMessage());
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
        }

        if (createdUserId == null || createdUserId.isBlank()) {
            // Fallback: look up user by email
            try {
                List<Map<String, Object>> users = client.get()
                        .uri(baseUrl + "/admin/realms/" + realm + "/users?email=" + email + "&exact=true")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (users != null && !users.isEmpty()) {
                    createdUserId = (String) users.get(0).get("id");
                }
            } catch (Exception e) {
                log.warn("Could not find created user ID by email: {}", e.getMessage());
            }
        }

        if (createdUserId == null) {
            throw new RuntimeException("Keycloak user created but user ID could not be resolved");
        }

        // 2. Fetch 'admin' role
        Map<String, Object> adminRole;
        try {
            adminRole = client.get()
                    .uri(baseUrl + "/admin/realms/" + realm + "/roles/admin")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Role 'admin' not found in Keycloak realm {}. Rolling back created user {}", realm, createdUserId);
            deleteUserCompensating(client, token, createdUserId);
            throw new KeycloakRoleNotFoundException("Keycloak realm role 'admin' not found. Create it in Keycloak realm.");
        }

        if (adminRole == null || !adminRole.containsKey("id")) {
            log.error("Role 'admin' definition invalid in Keycloak realm {}. Rolling back created user {}", realm, createdUserId);
            deleteUserCompensating(client, token, createdUserId);
            throw new KeycloakRoleNotFoundException("Keycloak realm role 'admin' not found. Create it in Keycloak realm.");
        }

        // 3. Assign role
        try {
            client.post()
                    .uri(baseUrl + "/admin/realms/" + realm + "/users/" + createdUserId + "/role-mappings/realm")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(adminRole))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to map 'admin' role to user {}. Rolling back created user", createdUserId);
            deleteUserCompensating(client, token, createdUserId);
            throw new RuntimeException("Failed to assign 'admin' role in Keycloak: " + e.getMessage(), e);
        }

        return new AdminUserResponse(
                createdUserId,
                email,
                firstName,
                lastName,
                "admin",
                true
        );
    }

    private void deleteUserCompensating(RestClient client, String token, String userId) {
        try {
            client.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Compensating rollback: deleted user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to delete user {} during compensating rollback: {}", userId, e.getMessage());
        }
    }

    public AdminListResponse getAdminUsers() {
        if (!isConfigured()) {
            return new AdminListResponse(false, Collections.emptyList());
        }

        try {
            RestClient client = restClientBuilder.build();
            String token = obtainAdminToken(client);

            Map<String, AdminUserSummary> userMap = new LinkedHashMap<>();

            // 1. Fetch super-admins
            try {
                List<Map<String, Object>> superAdmins = client.get()
                        .uri(baseUrl + "/admin/realms/" + realm + "/roles/super-admin/users")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (superAdmins != null) {
                    for (Map<String, Object> u : superAdmins) {
                        String id = (String) u.get("id");
                        userMap.put(id, new AdminUserSummary(
                                id,
                                (String) u.get("username"),
                                (String) u.get("email"),
                                (String) u.get("firstName"),
                                (String) u.get("lastName"),
                                "super-admin",
                                u.get("createdTimestamp") instanceof Number num ? num.longValue() : null
                        ));
                    }
                }
            } catch (Exception e) {
                log.debug("super-admin role or users query failed: {}", e.getMessage());
            }

            // 2. Fetch admins
            try {
                List<Map<String, Object>> admins = client.get()
                        .uri(baseUrl + "/admin/realms/" + realm + "/roles/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (admins != null) {
                    for (Map<String, Object> u : admins) {
                        String id = (String) u.get("id");
                        if (!userMap.containsKey(id)) {
                            userMap.put(id, new AdminUserSummary(
                                    id,
                                    (String) u.get("username"),
                                    (String) u.get("email"),
                                    (String) u.get("firstName"),
                                    (String) u.get("lastName"),
                                    "admin",
                                    u.get("createdTimestamp") instanceof Number num ? num.longValue() : null
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("admin role or users query failed: {}", e.getMessage());
            }

            return new AdminListResponse(true, new ArrayList<>(userMap.values()));
        } catch (Exception e) {
            log.warn("Error fetching admin users from Keycloak: {}", e.getMessage());
            return new AdminListResponse(false, Collections.emptyList());
        }
    }

    private String obtainAdminToken(RestClient client) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> tokenResponse = client.post()
                .uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain Keycloak admin access token");
        }
        return (String) tokenResponse.get("access_token");
    }
}
