package com.sme.maintenance.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.entities.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakId(String keycloakId);
}