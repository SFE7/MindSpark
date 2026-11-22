package com.mindspark.subscription.kafka;

import java.util.List;
import java.util.Map;

public record MindsparkKafkaEvent(
        String eventId,
        String eventType,
        String occurredAt,
        List<String> recipientUserIds,
        String title,
        String message,
        String actionUrl,
        Map<String, Object> metadata
) {}
