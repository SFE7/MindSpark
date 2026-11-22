package com.mindspark.subscription.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.EntitlementResponse;
import com.mindspark.subscription.exception.ForbiddenAccessException;
import com.mindspark.subscription.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/entitlements")
public class InternalEntitlementController {

    private final SubscriptionService subscriptionService;
    private final String expectedSecret;

    public InternalEntitlementController(
            SubscriptionService subscriptionService,
            @Value("${mindspark.internal.service-secret:mindspark-internal-dev-key}") String expectedSecret) {
        this.subscriptionService = subscriptionService;
        this.expectedSecret = expectedSecret;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<EntitlementResponse> getEntitlements(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey,
            @PathVariable String userId) {

        if (serviceKey == null || !serviceKey.equals(expectedSecret)) {
            log.warn("Unauthorized internal entitlement access attempt for userId: {}", userId);
            throw new ForbiddenAccessException("Invalid or missing internal service key");
        }

        return ResponseEntity.ok(subscriptionService.getEffectiveEntitlement(userId));
    }
}
