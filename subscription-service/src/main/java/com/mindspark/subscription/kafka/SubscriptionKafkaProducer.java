package com.mindspark.subscription.kafka;

public interface SubscriptionKafkaProducer {
    void publishSubscriptionChanged(
            String userId,
            String planCode,
            String previousPlanCode,
            String status,
            String source,
            String title,
            String message);
}
