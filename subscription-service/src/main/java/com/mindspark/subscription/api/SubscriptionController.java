package com.mindspark.subscription.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.CheckoutConfirmRequest;
import com.mindspark.subscription.dto.CheckoutConfirmResponse;
import com.mindspark.subscription.dto.CheckoutRequest;
import com.mindspark.subscription.dto.CheckoutResponse;
import com.mindspark.subscription.dto.PaymentConfigResponse;
import com.mindspark.subscription.dto.SubscribeRequest;
import com.mindspark.subscription.dto.SubscriptionMeResponse;
import com.mindspark.subscription.dto.UpgradeRequest;
import com.mindspark.subscription.security.JwtClaimsHelper;
import com.mindspark.subscription.service.SubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/payment-config")
    public ResponseEntity<PaymentConfigResponse> paymentConfig() {
        return ResponseEntity.ok(subscriptionService.getPaymentConfig());
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutRequest request) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        String userEmail = JwtClaimsHelper.getEmail(jwt);
        return ResponseEntity.ok(subscriptionService.createCheckout(userId, userEmail, request.planCode(), request.returnOrigin()));
    }

    @PostMapping("/checkout/confirm")
    public ResponseEntity<CheckoutConfirmResponse> confirmCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutConfirmRequest request) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        CheckoutConfirmResponse response = subscriptionService.confirmCheckout(userId, request.checkoutId());
        if ("PENDING_CONFIRMATION".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionMeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        return ResponseEntity.ok(subscriptionService.getEffectiveSubscription(userId));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionMeResponse> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubscribeRequest request) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        return ResponseEntity.ok(subscriptionService.subscribe(userId, request.planCode(), request.paymentMethodLabel()));
    }

    @PostMapping("/upgrade")
    public ResponseEntity<SubscriptionMeResponse> upgrade(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpgradeRequest request) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        return ResponseEntity.ok(subscriptionService.upgrade(userId, request.targetPlanCode(), request.paymentMethodLabel()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionMeResponse> cancel(@AuthenticationPrincipal Jwt jwt) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        return ResponseEntity.ok(subscriptionService.cancel(userId));
    }
}
