package com.mindspark.subscription.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mindspark.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSubscriptionKafkaProducer implements SubscriptionKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(NoOpSubscriptionKafkaProducer.class);

    @Override
    public void publishSubscriptionChanged(
            String userId,
            String planCode,
            String previousPlanCode,
            String status,
            String source,
            String title,
            String message) {
        log.debug("Kafka disabled - skipping subscription.changed publish for userId={}, planCode={}", userId, planCode);
    }
}
