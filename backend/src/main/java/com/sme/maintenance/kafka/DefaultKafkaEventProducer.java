package com.sme.maintenance.kafka;

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
import com.sme.maintenance.room.Room;
import com.sme.maintenance.votepool.VotePool;

@Service
@ConditionalOnProperty(name = "mindspark.kafka.enabled", havingValue = "true")
public class DefaultKafkaEventProducer implements KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaEventProducer.class);
    public static final String TOPIC_ROOM_CLOSED = "mindspark.room.closed";
    public static final String TOPIC_VOTE_POOL_CLOSED = "mindspark.vote-pool.closed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DefaultKafkaEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishRoomClosed(Room room, List<String> recipientSubs) {
        if (room == null || recipientSubs == null || recipientSubs.isEmpty()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("roomId", room.getId() != null ? room.getId().toString() : "");
        metadata.put("roomName", room.getName() != null ? room.getName() : "");

        MindsparkKafkaEvent event = new MindsparkKafkaEvent(
                UUID.randomUUID().toString(),
                "ROOM_CLOSED",
                Instant.now().toString(),
                recipientSubs,
                "Room Closed",
                "Room \"" + room.getName() + "\" has been closed.",
                "/room/" + room.getId(),
                metadata
        );

        dispatchAsync(TOPIC_ROOM_CLOSED, room.getId() != null ? room.getId().toString() : "", event);
    }

    @Override
    public void publishVotePoolClosed(Room room, VotePool pool, List<String> recipientSubs, String topIdeaTitle, long totalVotes) {
        if (room == null || pool == null || recipientSubs == null || recipientSubs.isEmpty()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("roomId", room.getId() != null ? room.getId().toString() : "");
        metadata.put("poolId", pool.getId() != null ? pool.getId().toString() : "");
        metadata.put("poolTitle", pool.getTitle() != null ? pool.getTitle() : "");
        metadata.put("topIdeaTitle", topIdeaTitle != null ? topIdeaTitle : "");
        metadata.put("totalVotes", totalVotes);

        String message = (topIdeaTitle != null && !topIdeaTitle.isBlank())
                ? "Voting ended for \"" + pool.getTitle() + "\". Leading idea: " + topIdeaTitle + " (" + totalVotes + " votes)."
                : "Voting ended for \"" + pool.getTitle() + "\" with " + totalVotes + " total votes.";

        MindsparkKafkaEvent event = new MindsparkKafkaEvent(
                UUID.randomUUID().toString(),
                "VOTE_POOL_CLOSED",
                Instant.now().toString(),
                recipientSubs,
                "Vote Pool Closed",
                message,
                "/room/" + room.getId(),
                metadata
        );

        dispatchAsync(TOPIC_VOTE_POOL_CLOSED, room.getId() != null ? room.getId().toString() : "", event);
    }

    private void dispatchAsync(String topic, String key, MindsparkKafkaEvent event) {
        Runnable sendTask = () -> {
            try {
                String payloadJson = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, key, payloadJson).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish Kafka event [{}] to topic {}: {}", event.eventType(), topic, ex.getMessage());
                    } else {
                        log.info("Published Kafka event [{}] to topic {} for key {}", event.eventType(), topic, key);
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
