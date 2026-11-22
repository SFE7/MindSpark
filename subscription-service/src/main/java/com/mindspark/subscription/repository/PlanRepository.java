package com.mindspark.subscription.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mindspark.subscription.entity.Plan;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCode(String code);
    List<Plan> findByActiveTrueOrderBySortOrderAsc();
    List<Plan> findAllByOrderBySortOrderAsc();
    boolean existsByCode(String code);
}
