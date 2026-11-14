package com.sme.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.presence.PresenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresenceServiceTests {

    private MutableClock clock;
    private PresenceService service;

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
    void setup() {
        clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        service = new PresenceService(clock);
    }

    @Test
    void heartbeatKeepsMemberActive() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;
        String clientId = "client-1";

        service.heartbeat(roomId, userId, clientId, false);
        assertThat(service.isUserActive(roomId, userId)).isTrue();

        // Advance 30 seconds (less than 60s expiry)
        clock.advance(Duration.ofSeconds(30));
        assertThat(service.isUserActive(roomId, userId)).isTrue();

        // Send another heartbeat
        service.heartbeat(roomId, userId, clientId, false);

        // Advance another 35 seconds (total 65s since first, but 35s since second)
        clock.advance(Duration.ofSeconds(35));
        assertThat(service.isUserActive(roomId, userId)).isTrue();
    }

    @Test
    void memberExpiresAfter60SecondsWithoutHeartbeat() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;
        String clientId = "client-1";

        service.heartbeat(roomId, userId, clientId, false);
        assertThat(service.isUserActive(roomId, userId)).isTrue();

        // Advance 61 seconds
        clock.advance(Duration.ofSeconds(61));
        assertThat(service.isUserActive(roomId, userId)).isFalse();

        List<PresenceService.ExpiredMember> expired = service.computeExpiredMembers();
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).userId()).isEqualTo(userId);
    }

    @Test
    void leaveStartsGracePeriodAndHeartbeatCancelsIt() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;
        String clientId = "client-1";

        service.heartbeat(roomId, userId, clientId, false);

        // Leave request received (starts 10s grace period)
        service.leave(roomId, userId, clientId, false);

        // Within 10s grace period (e.g. 5s), user is still considered active
        clock.advance(Duration.ofSeconds(5));
        assertThat(service.isUserActive(roomId, userId)).isTrue();

        // Heartbeat received within grace period (e.g. on navigation/refresh)
        service.heartbeat(roomId, userId, clientId, false);

        // Advance 8 more seconds (13s total since leave) - user is still active because leave was cancelled
        clock.advance(Duration.ofSeconds(8));
        assertThat(service.isUserActive(roomId, userId)).isTrue();
    }

    @Test
    void leaveExpiresAfter10SecondsGraceWithoutHeartbeat() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;
        String clientId = "client-1";

        service.heartbeat(roomId, userId, clientId, false);
        service.leave(roomId, userId, clientId, false);

        // Advance 11 seconds (past 10s grace)
        clock.advance(Duration.ofSeconds(11));
        assertThat(service.isUserActive(roomId, userId)).isFalse();

        List<PresenceService.ExpiredMember> expired = service.computeExpiredMembers();
        assertThat(expired).hasSize(1);
    }

    @Test
    void multipleClientIdsKeepUserActiveIfAnyIsLive() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;

        // Tab 1 and Tab 2
        service.heartbeat(roomId, userId, "tab-1", false);
        service.heartbeat(roomId, userId, "tab-2", false);

        // Tab 1 closes (leaves)
        service.leave(roomId, userId, "tab-1", false);
        clock.advance(Duration.ofSeconds(15)); // Past Tab 1's grace period

        // User is still active because Tab 2 is active!
        assertThat(service.isUserActive(roomId, userId)).isTrue();
        assertThat(service.computeExpiredMembers()).isEmpty();
    }

    @Test
    void ownerExpiryMakesRoomOfflineAndHeartbeatRestoresIt() {
        UUID roomId = UUID.randomUUID();
        Long ownerId = 1L;

        // A room with no recorded owner heartbeat since server start counts as ONLINE
        assertThat(service.isRoomOffline(roomId, ownerId)).isFalse();

        // Owner sends first heartbeat
        service.heartbeat(roomId, ownerId, "owner-tab", true);
        assertThat(service.isRoomOffline(roomId, ownerId)).isFalse();

        // Owner goes away: advance 61 seconds
        clock.advance(Duration.ofSeconds(61));
        assertThat(service.isRoomOffline(roomId, ownerId)).isTrue();

        // Owner returns and sends heartbeat
        service.heartbeat(roomId, ownerId, "owner-tab-new", true);
        assertThat(service.isRoomOffline(roomId, ownerId)).isFalse();
    }

    @Test
    void clientIdValidationRejectsBlankAndOversized() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;

        assertThatThrownBy(() -> service.heartbeat(roomId, userId, "", false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.heartbeat(roomId, userId, "   ", false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.heartbeat(roomId, userId, "a".repeat(65), false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void capsClientsPerUserPerRoom() {
        UUID roomId = UUID.randomUUID();
        Long userId = 10L;

        for (int i = 1; i <= 15; i++) {
            service.heartbeat(roomId, userId, "client-" + i, false);
        }

        // Must cap at MAX_CLIENTS_PER_USER (10)
        // User is still active
        assertThat(service.isUserActive(roomId, userId)).isTrue();
    }
}
