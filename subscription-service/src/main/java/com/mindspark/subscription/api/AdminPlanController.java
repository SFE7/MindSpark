package com.mindspark.subscription.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.AdminPlanResponse;
import com.mindspark.subscription.dto.CreatePlanRequest;
import com.mindspark.subscription.dto.UpdatePlanRequest;
import com.mindspark.subscription.service.AdminSubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/plans")
public class AdminPlanController {

    private final AdminSubscriptionService adminSubscriptionService;

    public AdminPlanController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<List<AdminPlanResponse>> getAllPlans() {
        return ResponseEntity.ok(adminSubscriptionService.getAllPlans());
    }

    @PostMapping
    public ResponseEntity<AdminPlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest req) {
        AdminPlanResponse created = adminSubscriptionService.createPlan(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminPlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest req) {
        return ResponseEntity.ok(adminSubscriptionService.updatePlan(id, req));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminPlanResponse> toggleStatus(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Boolean active = (body != null && body.containsKey("active")) ? body.get("active") : null;
        return ResponseEntity.ok(adminSubscriptionService.togglePlanStatus(id, active));
    }
}
