package com.mindspark.subscription.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mindspark.subscription.entity.Subscription;
import com.mindspark.subscription.entity.SubscriptionStatus;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID>, JpaSpecificationExecutor<Subscription> {
    Optional<Subscription> findByUserIdAndStatus(String userId, SubscriptionStatus status);
    List<Subscription> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByStatus(SubscriptionStatus status);
    long countByPlanIdAndStatus(UUID planId, SubscriptionStatus status);
    List<Subscription> findByStatus(SubscriptionStatus status);
}
