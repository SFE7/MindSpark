package com.mindspark.subscription.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.BillingRecordResponse;
import com.mindspark.subscription.security.JwtClaimsHelper;
import com.mindspark.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final SubscriptionService subscriptionService;

    public BillingController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<BillingRecordResponse>> getHistory(@AuthenticationPrincipal Jwt jwt) {
        String userId = JwtClaimsHelper.getUserId(jwt);
        return ResponseEntity.ok(subscriptionService.getBillingHistory(userId));
    }
}
