package com.mindspark.subscription.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.BillingStatsResponse;
import com.mindspark.subscription.service.AdminSubscriptionService;

@RestController
@RequestMapping("/api/admin/stats/billing")
public class AdminBillingStatsController {

    private final AdminSubscriptionService adminSubscriptionService;

    public AdminBillingStatsController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<BillingStatsResponse> getBillingStats() {
        return ResponseEntity.ok(adminSubscriptionService.getBillingStats());
    }
}
