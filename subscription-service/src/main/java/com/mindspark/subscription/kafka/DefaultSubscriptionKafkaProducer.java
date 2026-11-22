package com.mindspark.subscription.kafka;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "mindspark.kafka.enabled", havingValue = "true")
public class DefaultSubscriptionKafkaProducer implements SubscriptionKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionKafkaProducer.class);
    public static final String TOPIC_SUBSCRIPTION_CHANGED = "mindspark.subscription.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DefaultSubscriptionKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishSubscriptionChanged(
            String userId,
            String planCode,
            String previousPlanCode,
            String status,
            String source,
            String title,
            String message) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("planCode", planCode != null ? planCode : "");
        metadata.put("previousPlanCode", previousPlanCode != null ? previousPlanCode : "");
        metadata.put("status", status != null ? status : "");
        metadata.put("source", source != null ? source : "");

        MindsparkKafkaEvent event = new MindsparkKafkaEvent(
                UUID.randomUUID().toString(),
                "SUBSCRIPTION_CHANGED",
                Instant.now().toString(),
                List.of(userId),
                title != null ? title : "Subscription Updated",
                message != null ? message : "Your subscription plan is now " + planCode + ".",
                "/billing",
                metadata
        );

        dispatchAsync(TOPIC_SUBSCRIPTION_CHANGED, userId, event);
    }

    private void dispatchAsync(String topic, String key, MindsparkKafkaEvent event) {
        Runnable sendTask = () -> {
            try {
                String payloadJson = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, key, payloadJson).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish Kafka event [{}] to topic {}: {}", event.eventType(), topic, ex.getMessage());
                    } else {
                        log.info("Published Kafka event [{}] to topic {} for user {}", event.eventType(), topic, key);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to serialize or send Kafka event to topic {}: {}", topic, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
        } else {
            sendTask.run();
        }
    }
}
