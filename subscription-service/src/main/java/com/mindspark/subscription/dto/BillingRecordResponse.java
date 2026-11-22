package com.mindspark.subscription.dto;

import java.time.Instant;
import java.util.UUID;

public record BillingRecordResponse(
    UUID id,
    String invoiceNumber,
    String eventType,
    String planCode,
    String planName,
    int amountCents,
    String currency,
    String status,
    String paymentMethodSummary,
    Instant createdAt
) {}
