package com.sme.maintenance.admin;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.admin.dto.AdminUserDirectoryResponse;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUsersController {

    private final UserRepository userRepository;

    public AdminUsersController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserDirectoryResponse>> getUsersDirectory() {
        List<User> users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<AdminUserDirectoryResponse> response = users.stream()
                .map(this::toDirectoryResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private AdminUserDirectoryResponse toDirectoryResponse(User user) {
        return new AdminUserDirectoryResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getEmail(),
                user.getUsername(),
                user.getCreatedAt()
        );
    }
}
