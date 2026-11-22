package com.mindspark.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspark.subscription.kafka.DefaultSubscriptionKafkaProducer;
import com.mindspark.subscription.kafka.SubscriptionKafkaProducer;

class SubscriptionKafkaProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private DefaultSubscriptionKafkaProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        producer = new DefaultSubscriptionKafkaProducer(kafkaTemplate, objectMapper);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
    }

    @Test
    void testPublishSubscriptionChanged_SendsJsonPayload() throws Exception {
        producer.publishSubscriptionChanged(
                "user-sub-123",
                "PREMIUM",
                "FREE",
                "ACTIVE",
                "UPGRADE",
                "Subscription Upgraded",
                "Your subscription is now PREMIUM."
        );

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("mindspark.subscription.changed", topicCaptor.getValue());
        assertEquals("user-sub-123", keyCaptor.getValue());

        String json = payloadCaptor.getValue();
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertNotNull(root.get("eventId").asText());
        assertEquals("SUBSCRIPTION_CHANGED", root.get("eventType").asText());
        assertEquals("Subscription Upgraded", root.get("title").asText());
        assertEquals("/billing", root.get("actionUrl").asText());
        assertTrue(root.get("recipientUserIds").isArray());
        assertEquals("user-sub-123", root.get("recipientUserIds").get(0).asText());
        assertEquals("PREMIUM", root.get("metadata").get("planCode").asText());
        assertEquals("FREE", root.get("metadata").get("previousPlanCode").asText());
        assertEquals("ACTIVE", root.get("metadata").get("status").asText());
        assertEquals("UPGRADE", root.get("metadata").get("source").asText());
    }

    @Test
    void testPublishSubscriptionChanged_IgnoresBlankUserId() {
        producer.publishSubscriptionChanged("", "PREMIUM", "FREE", "ACTIVE", "UPGRADE", "t", "m");
        verify(kafkaTemplate, org.mockito.Mockito.never()).send(any(), any(), any());
    }
}
