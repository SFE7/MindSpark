package com.sme.maintenance.websocket;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.services.AuthenticatedUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomWebSocketHandlerTests {

    private MutableClock clock;
    private JwtDecoder jwtDecoder;
    private AuthenticatedUserService authenticatedUserService;
    private RoomRepository roomRepository;
    private PresenceService presenceService;
    private RoomBroadcastService broadcastService;
    private ObjectMapper objectMapper;
    private RoomWebSocketHandler handler;

    private static class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant initial) {
            this.now = initial;
        }

        public void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-14T12:00:00Z"));
        jwtDecoder = mock(JwtDecoder.class);
        authenticatedUserService = mock(AuthenticatedUserService.class);
        roomRepository = mock(RoomRepository.class);
        presenceService = new PresenceService(clock, 60, 5);
        objectMapper = new ObjectMapper();
        broadcastService = new RoomBroadcastService(roomRepository, objectMapper, clock);

        handler = new RoomWebSocketHandler(
                jwtDecoder,
                authenticatedUserService,
                roomRepository,
                presenceService,
                broadcastService,
                objectMapper,
                clock,
                5000,   // authTimeoutMs
                5000,   // pingIntervalMs
                10000,  // pongTimeoutMs
                30000,  // tokenExpiryGraceMs
                8192,   // maxTextMessageSize
                100,    // maxUnauth
                10      // maxPerUser
        );
    }

    private WebSocketSession createMockSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Jwt createMockJwt(String subject, Instant expiresAt) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getExpiresAt()).thenReturn(expiresAt);
        return jwt;
    }

    private User createMockUser(Long id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }

    private Room createMockRoom(UUID id, Long ownerId, List<Long> memberIds) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getOwnerId()).thenReturn(ownerId);
        when(room.getMemberIds()).thenReturn(new java.util.HashSet<>(memberIds));
        return room;
    }

    @Test
    void authTimeoutClosesUnauthenticatedSessionAfter5Seconds() throws Exception {
        WebSocketSession session = createMockSession("s1");
        handler.afterConnectionEstablished(session);

        // Advance 4 seconds: still within 5s timeout
        clock.advance(Duration.ofSeconds(4));
        handler.runMaintenance();
        verify(session, never()).close(any(CloseStatus.class));

        // Advance 2 more seconds: total 6 seconds, exceeds 5s timeout
        clock.advance(Duration.ofSeconds(2));
        handler.runMaintenance();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void badTokenClosesSessionWith4401() throws Exception {
        WebSocketSession session = createMockSession("s-bad-token");
        handler.afterConnectionEstablished(session);

        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("Invalid token signature"));

        String payload = objectMapper.writeValueAsString(Map.of(
                "type", "auth",
                "token", "bad-jwt-string",
                "roomId", UUID.randomUUID().toString(),
                "clientId", "c1"
        ));

        handler.handleTextMessage(session, new TextMessage(payload));
        verify(session).close(new CloseStatus(4401, "Invalid token"));
    }

    @Test
    void nonMemberClosesSessionWith4403() throws Exception {
        WebSocketSession session = createMockSession("s-non-member");
        handler.afterConnectionEstablished(session);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-1", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(100L, "intruder");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 2L)); // 100L is not a member

        when(jwtDecoder.decode("valid-jwt")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        String payload = objectMapper.writeValueAsString(Map.of(
                "type", "auth",
                "token", "valid-jwt",
                "roomId", roomId.toString(),
                "clientId", "c1"
        ));

        handler.handleTextMessage(session, new TextMessage(payload));
        verify(session).close(new CloseStatus(4403, "Forbidden: not a room member"));
    }

    @Test
    void successfulAuthRegistersSessionAndSendsAck() throws Exception {
        WebSocketSession session = createMockSession("s-member");
        handler.afterConnectionEstablished(session);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-2", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(2L, "member-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 2L));

        when(jwtDecoder.decode("valid-jwt")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        String payload = objectMapper.writeValueAsString(Map.of(
                "type", "auth",
                "token", "valid-jwt",
                "roomId", roomId.toString(),
                "clientId", "c1"
        ));

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<TextMessage> msgCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getPayload()).contains("auth.success");
        assertThat(broadcastService.getSessionCount(roomId, 2L)).isEqualTo(1);
        assertThat(presenceService.isUserActive(roomId, 2L)).isTrue();
    }

    @Test
    void liveSocketWithoutRestHeartbeatFor60sIsNotRemoved() throws Exception {
        WebSocketSession session = createMockSession("s-live-socket");
        handler.afterConnectionEstablished(session);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-3", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(3L, "socket-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 3L));

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "c3"
        ))));

        // User is active
        assertThat(presenceService.isUserActive(roomId, 3L)).isTrue();

        // Advance 70 seconds (> 60s REST expiry) with pongs arriving
        clock.advance(Duration.ofSeconds(40));
        handler.handlePongMessage(session, new PongMessage());
        clock.advance(Duration.ofSeconds(40));
        handler.handlePongMessage(session, new PongMessage());

        // Presence check: still active because the socket is live!
        assertThat(presenceService.isUserActive(roomId, 3L)).isTrue();
        assertThat(presenceService.computeExpiredMembers()).isEmpty();
    }

    @Test
    void socketCloseStartsGraceAndReconnectWithinGraceCancelsIt() throws Exception {
        WebSocketSession session1 = createMockSession("s-tab-1");
        handler.afterConnectionEstablished(session1);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-4", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(4L, "reconnect-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 4L));

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        handler.handleTextMessage(session1, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "c4"
        ))));

        // Socket closes (e.g. user refreshes)
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // Advance 2 seconds (within 5s grace period)
        clock.advance(Duration.ofSeconds(2));
        assertThat(presenceService.isUserActive(roomId, 4L)).isTrue();

        // Reconnects with same clientId
        WebSocketSession session2 = createMockSession("s-tab-1-reconnect");
        handler.afterConnectionEstablished(session2);
        handler.handleTextMessage(session2, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "c4"
        ))));

        // Advance 6 more seconds (8s total since initial close): user is still active!
        clock.advance(Duration.ofSeconds(6));
        assertThat(presenceService.isUserActive(roomId, 4L)).isTrue();
        assertThat(presenceService.computeExpiredMembers()).isEmpty();
    }

    @Test
    void twoSocketsForOneUserKeepsUserActiveWhenOneCloses() throws Exception {
        WebSocketSession sessionA = createMockSession("s-tab-a");
        WebSocketSession sessionB = createMockSession("s-tab-b");
        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-5", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(5L, "multi-tab-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 5L));

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        handler.handleTextMessage(sessionA, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "tab-a"
        ))));
        handler.handleTextMessage(sessionB, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "tab-b"
        ))));

        assertThat(broadcastService.getSessionCount(roomId, 5L)).isEqualTo(2);

        // Tab A closes
        handler.afterConnectionClosed(sessionA, CloseStatus.NORMAL);
        clock.advance(Duration.ofSeconds(10)); // Grace for tab-a has passed

        // User is still active because Tab B is alive!
        assertThat(presenceService.isUserActive(roomId, 5L)).isTrue();
        assertThat(presenceService.computeExpiredMembers()).isEmpty();
    }

    @Test
    void tokenExpiryClosesSocketWith4401After30sGrace() throws Exception {
        WebSocketSession session = createMockSession("s-expiry");
        handler.afterConnectionEstablished(session);

        UUID roomId = UUID.randomUUID();
        Instant tokenExpiry = clock.instant().plus(Duration.ofSeconds(60));
        Jwt jwt = createMockJwt("user-sub-exp", tokenExpiry);
        User user = createMockUser(6L, "expiring-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 6L));

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "c6"
        ))));

        // Advance to 65 seconds (token expired 5 seconds ago, still within 30s grace)
        // Keep pong fresh so ping timeout doesn't fire
        clock.advance(Duration.ofSeconds(65));
        handler.handlePongMessage(session, new PongMessage());
        handler.runMaintenance();
        verify(session, never()).close(any(CloseStatus.class));

        // Advance 30 more seconds: 35 seconds past token expiry (exceeds 30s grace)
        clock.advance(Duration.ofSeconds(30));
        handler.handlePongMessage(session, new PongMessage());
        handler.runMaintenance();
        verify(session).close(new CloseStatus(4401, "Token expired"));
    }

    @Test
    void removedMemberSocketIsClosedAndReceivesNoEventAfterRemoval() throws Exception {
        WebSocketSession session = createMockSession("s-removed");
        handler.afterConnectionEstablished(session);

        UUID roomId = UUID.randomUUID();
        Jwt jwt = createMockJwt("user-sub-rem", clock.instant().plus(Duration.ofHours(1)));
        User user = createMockUser(7L, "removed-user");
        Room room = createMockRoom(roomId, 1L, List.of(1L, 7L));

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(authenticatedUserService.getCurrentUser(jwt)).thenReturn(user);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "auth", "token", "token", "roomId", roomId.toString(), "clientId", "c7"
        ))));

        // Explicit close via broadcastService (e.g. owner removed user)
        broadcastService.closeUserSockets(roomId, 7L, 4403, "Removed from room");
        verify(session).close(new CloseStatus(4403, "Removed from room"));

        // Clear mock invocations from the auth step (which sent auth.success)
        clearInvocations(session);

        // Now broadcast an event to the room: removed session must receive nothing
        RoomEvent event = RoomEvent.roomOffline(roomId, 1L, clock.instant(), true);
        broadcastService.broadcast(roomId, event);
        verify(session, never()).sendMessage(any(TextMessage.class));
    }
}
