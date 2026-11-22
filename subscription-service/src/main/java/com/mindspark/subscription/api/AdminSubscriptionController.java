package com.mindspark.subscription.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.AdminAssignRequest;
import com.mindspark.subscription.dto.AdminCancelResponse;
import com.mindspark.subscription.dto.AdminSubscriptionResponse;
import com.mindspark.subscription.dto.AdminSubscriptionsPageResponse;
import com.mindspark.subscription.service.AdminSubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/subscriptions")
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    public AdminSubscriptionController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<AdminSubscriptionsPageResponse> getSubscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminSubscriptionService.getSubscriptions(status, planCode, userId, page, size));
    }

    @PostMapping("/assign")
    public ResponseEntity<AdminSubscriptionResponse> assignPlan(
            @Valid @RequestBody AdminAssignRequest req,
            Authentication auth) {
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "admin";
        return ResponseEntity.ok(adminSubscriptionService.adminAssignPlan(req, actor));
    }

    @PostMapping("/{userId}/cancel")
    public ResponseEntity<AdminCancelResponse> cancelSubscription(
            @PathVariable String userId,
            Authentication auth) {
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "admin";
        return ResponseEntity.ok(adminSubscriptionService.adminCancelSubscription(userId, actor));
    }
}
