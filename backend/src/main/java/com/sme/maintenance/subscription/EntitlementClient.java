package com.sme.maintenance.subscription;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EntitlementClient {

    private final RestClient restClient;
    private final String internalSecret;

    @Value("${mindspark.subscription.fallback-enabled:false}")
    private boolean fallbackEnabled;

    public EntitlementClient(
            @Value("${subscription.service.url:http://subscription-service:8083}") String subscriptionServiceUrl,
            @Value("${internal.service.secret:mindspark-internal-dev-key}") String internalSecret) {
        this(null, subscriptionServiceUrl, internalSecret);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EntitlementClient(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${subscription.service.url:http://subscription-service:8083}") String subscriptionServiceUrl,
            @Value("${internal.service.secret:mindspark-internal-dev-key}") String internalSecret) {
        this.internalSecret = internalSecret;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(2000));
        requestFactory.setReadTimeout(Duration.ofMillis(3000));

        RestClient.Builder builder = (restClientBuilder != null) ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(subscriptionServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @CircuitBreaker(name = "subscriptionEntitlement", fallbackMethod = "entitlementsFallback")
    public EntitlementDto getEntitlements(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            if (fallbackEnabled) {
                log.warn("Missing keycloakSub with test fallback enabled; returning default free entitlements");
                return defaultFreeEntitlement(keycloakSub);
            }
            throw new SubscriptionServiceUnavailableException("Keycloak user ID (sub) is missing or blank");
        }

        try {
            return restClient.get()
                    .uri("/internal/entitlements/{userId}", keycloakSub)
                    .header("X-Internal-Service-Key", internalSecret)
                    .retrieve()
                    .body(EntitlementDto.class);
        } catch (Exception e) {
            log.error("Failed to fetch entitlements from subscription-service for user {}: {}", keycloakSub, e.getMessage());
            if (fallbackEnabled) {
                log.warn("Fallback enabled in current profile; returning default free entitlements");
                return defaultFreeEntitlement(keycloakSub);
            }
            throw new SubscriptionServiceUnavailableException("Subscription service is temporarily unavailable. Please try again later.", e);
        }
    }

    public EntitlementDto entitlementsFallback(String keycloakSub, Throwable t) {
        log.warn("Circuit breaker fallback triggered for user {}: {}", keycloakSub, t.getMessage());
        if (fallbackEnabled) {
            log.warn("Fallback enabled in current profile; returning default free entitlements");
            return defaultFreeEntitlement(keycloakSub);
        }
        if (t instanceof SubscriptionServiceUnavailableException ssue) {
            throw ssue;
        }
        throw new SubscriptionServiceUnavailableException("Subscription service is temporarily unavailable. Please try again later.", t);
    }

    public EntitlementDto defaultFreeEntitlement(String keycloakSub) {
        return new EntitlementDto(
                keycloakSub,
                "FREE",
                "Free",
                "FREE",
                3,
                5,
                10
        );
    }
}
