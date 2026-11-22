package com.mindspark.subscription.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_records")
public class BillingRecord {

    @Id
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private BillingEventType eventType;

    // Immutable snapshot columns
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingRecordStatus status;

    @Column(name = "payment_method_summary", nullable = false, length = 100)
    private String paymentMethodSummary;

    @Column(name = "external_checkout_id", length = 128, unique = true)
    private String externalCheckoutId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
