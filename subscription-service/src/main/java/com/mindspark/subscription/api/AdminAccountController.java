package com.mindspark.subscription.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.subscription.dto.AdminListResponse;
import com.mindspark.subscription.dto.AdminUserResponse;
import com.mindspark.subscription.dto.CreateAdminRequest;
import com.mindspark.subscription.service.KeycloakAdminUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/admins")
public class AdminAccountController {

    private final KeycloakAdminUserService keycloakAdminUserService;

    public AdminAccountController(KeycloakAdminUserService keycloakAdminUserService) {
        this.keycloakAdminUserService = keycloakAdminUserService;
    }

    @GetMapping
    public ResponseEntity<AdminListResponse> getAdmins() {
        return ResponseEntity.ok(keycloakAdminUserService.getAdminUsers());
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        AdminUserResponse created = keycloakAdminUserService.createAdminUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
