package com.mindspark.subscription.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KeycloakRoleSyncService {

    public enum RoleSyncStatus {
        SUCCESS,
        SKIPPED_UNCONFIGURED,
        FAILED
    }

    private final RestClient.Builder restClientBuilder;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakRoleSyncService(
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

    @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "syncRoleFallback")
    public RoleSyncStatus assignPremiumRole(String userId) {
        return syncRole(userId, true);
    }

    @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "syncRoleFallback")
    public RoleSyncStatus removePremiumRole(String userId) {
        return syncRole(userId, false);
    }

    public RoleSyncStatus syncRoleFallback(String userId, Throwable t) {
        log.warn("Circuit breaker keycloakAdmin fallback triggered for user {}: {}", userId, t.getMessage());
        return RoleSyncStatus.FAILED;
    }

    private RoleSyncStatus syncRole(String userId, boolean assign) {
        if (!isConfigured()) {
            log.warn("Keycloak admin credentials not configured (KEYCLOAK_ADMIN_CLIENT_ID / KEYCLOAK_ADMIN_CLIENT_SECRET). Skipping realm role sync for user {}", userId);
            return RoleSyncStatus.SKIPPED_UNCONFIGURED;
        }

        try {
            RestClient client = restClientBuilder.build();

            // 1. Fetch admin access token via client_credentials
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
                log.warn("Failed to obtain Keycloak admin token: empty response");
                return RoleSyncStatus.FAILED;
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // 2. Fetch the "premium" role definition
            Map<String, Object> roleObj = client.get()
                    .uri(baseUrl + "/admin/realms/" + realm + "/roles/premium")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (roleObj == null || !roleObj.containsKey("id")) {
                log.warn("Role 'premium' not found in Keycloak realm '{}'", realm);
                return RoleSyncStatus.FAILED;
            }

            List<Map<String, Object>> roleList = List.of(roleObj);

            // 3. Assign or remove role mapping
            if (assign) {
                client.post()
                        .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(roleList)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully assigned 'premium' role to Keycloak user {}", userId);
            } else {
                client.method(org.springframework.http.HttpMethod.DELETE)
                        .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(roleList)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully removed 'premium' role from Keycloak user {}", userId);
            }

            return RoleSyncStatus.SUCCESS;
        } catch (Exception e) {
            log.warn("Error synchronizing Keycloak realm role for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to synchronize Keycloak realm role for user " + userId, e);
        }
    }
}
