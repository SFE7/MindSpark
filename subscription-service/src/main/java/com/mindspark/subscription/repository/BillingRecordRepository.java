package com.mindspark.subscription.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mindspark.subscription.entity.BillingRecord;
import com.mindspark.subscription.entity.BillingRecordStatus;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {
    @Query("SELECT b FROM BillingRecord b WHERE b.userId = :userId ORDER BY b.createdAt DESC, b.invoiceNumber DESC")
    List<BillingRecord> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(b.amountCents), 0) FROM BillingRecord b WHERE b.status = :status")
    long sumTotalRevenueCents(@Param("status") BillingRecordStatus status);

    long countByCreatedAtAfter(Instant after);

    List<BillingRecord> findTop10ByOrderByCreatedAtDesc();

    java.util.Optional<BillingRecord> findByExternalCheckoutId(String externalCheckoutId);
}
