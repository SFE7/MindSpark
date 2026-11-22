package com.sme.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.kafka.DefaultKafkaEventProducer;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.votepool.VotePool;

class BackendKafkaProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private DefaultKafkaEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        producer = new DefaultKafkaEventProducer(kafkaTemplate, objectMapper);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
    }

    @Test
    void testPublishRoomClosed_SendsCorrectKafkaMessage() throws Exception {
        User owner = new User();
        owner.setId(1L);
        owner.setKeycloakId("owner-sub-123");
        owner.setUsername("alice");

        Room room = new Room("Sprint Retro", "ABC12345", owner);
        // set ID by reflection or let it generate if not null
        java.lang.reflect.Field idField = Room.class.getDeclaredField("id");
        idField.setAccessible(true);
        UUID roomId = UUID.randomUUID();
        idField.set(room, roomId);

        producer.publishRoomClosed(room, List.of("owner-sub-123", "member-sub-456"));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("mindspark.room.closed", topicCaptor.getValue());
        assertEquals(roomId.toString(), keyCaptor.getValue());

        String json = payloadCaptor.getValue();
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertEquals("ROOM_CLOSED", root.get("eventType").asText());
        assertEquals("Room Closed", root.get("title").asText());
        assertEquals("/room/" + roomId, root.get("actionUrl").asText());
        assertTrue(root.get("recipientUserIds").isArray());
        assertEquals(2, root.get("recipientUserIds").size());
        assertEquals(roomId.toString(), root.get("metadata").get("roomId").asText());
        assertEquals("Sprint Retro", root.get("metadata").get("roomName").asText());
    }

    @Test
    void testPublishVotePoolClosed_SendsCorrectKafkaMessage() throws Exception {
        User owner = new User();
        owner.setId(1L);
        owner.setKeycloakId("owner-sub-123");
        owner.setUsername("alice");

        Room room = new Room("Sprint Retro", "ABC12345", owner);
        java.lang.reflect.Field roomIdField = Room.class.getDeclaredField("id");
        roomIdField.setAccessible(true);
        UUID roomId = UUID.randomUUID();
        roomIdField.set(room, roomId);

        VotePool pool = new VotePool(room, "Feature Priority", 3, true, owner);
        java.lang.reflect.Field poolIdField = VotePool.class.getDeclaredField("id");
        poolIdField.setAccessible(true);
        UUID poolId = UUID.randomUUID();
        poolIdField.set(pool, poolId);

        producer.publishVotePoolClosed(room, pool, List.of("owner-sub-123"), "Dark Mode", 15L);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("mindspark.vote-pool.closed", topicCaptor.getValue());
        assertEquals(roomId.toString(), keyCaptor.getValue());

        String json = payloadCaptor.getValue();
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertEquals("VOTE_POOL_CLOSED", root.get("eventType").asText());
        assertEquals("Vote Pool Closed", root.get("title").asText());
        assertEquals("/room/" + roomId, root.get("actionUrl").asText());
        assertEquals("Feature Priority", root.get("metadata").get("poolTitle").asText());
        assertEquals("Dark Mode", root.get("metadata").get("topIdeaTitle").asText());
        assertEquals(15, root.get("metadata").get("totalVotes").asLong());
    }

    @Test
    void testPublishRoomClosed_EmptyRecipientsIgnored() {
        producer.publishRoomClosed(null, List.of());
        verify(kafkaTemplate, org.mockito.Mockito.never()).send(any(), any(), any());
    }
}
