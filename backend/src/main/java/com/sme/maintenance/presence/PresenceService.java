package com.sme.maintenance.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PresenceService {

    public static final Duration HEARTBEAT_EXPIRY = Duration.ofSeconds(60);
    public static final Duration LEAVE_GRACE = Duration.ofSeconds(5);
    public static final int MAX_CLIENTS_PER_USER = 10;

    private Clock clock;
    private Duration heartbeatExpiry;
    private Duration leaveGrace;

    public static class ClientSession {
        private Instant lastHeartbeat;
        private Instant leaveStartedAt;
        private boolean hasActiveSocket;
        private final Duration heartbeatExpiry;
        private final Duration leaveGrace;

        public ClientSession(Instant now, Duration heartbeatExpiry, Duration leaveGrace) {
            this.lastHeartbeat = now;
            this.leaveStartedAt = null;
            this.hasActiveSocket = false;
            this.heartbeatExpiry = heartbeatExpiry;
            this.leaveGrace = leaveGrace;
        }

        public Instant getLastHeartbeat() {
            return lastHeartbeat;
        }

        public void setLastHeartbeat(Instant lastHeartbeat) {
            this.lastHeartbeat = lastHeartbeat;
        }

        public Instant getLeaveStartedAt() {
            return leaveStartedAt;
        }

        public void setLeaveStartedAt(Instant leaveStartedAt) {
            this.leaveStartedAt = leaveStartedAt;
        }

        public boolean isHasActiveSocket() {
            return hasActiveSocket;
        }

        public void setHasActiveSocket(boolean hasActiveSocket) {
            this.hasActiveSocket = hasActiveSocket;
        }

        public boolean isActive(Instant now) {
            if (hasActiveSocket) {
                return true;
            }
            if (leaveStartedAt != null) {
                return Duration.between(leaveStartedAt, now).compareTo(leaveGrace) < 0;
            }
            return Duration.between(lastHeartbeat, now).compareTo(heartbeatExpiry) < 0;
        }
    }

    public record ExpiredMember(UUID roomId, Long userId) {}

    // roomId -> (userId -> (clientId -> ClientSession))
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>>> roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> seenOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> roomLocks = new ConcurrentHashMap<>();

    @Autowired
    public PresenceService(@Autowired(required = false) Clock clock,
                           @Value("${mindspark.presence.heartbeat-expiry-seconds:60}") long heartbeatExpirySeconds,
                           @Value("${mindspark.presence.leave-grace-seconds:5}") long leaveGraceSeconds) {
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.heartbeatExpiry = Duration.ofSeconds(heartbeatExpirySeconds);
        this.leaveGrace = Duration.ofSeconds(leaveGraceSeconds);
    }

    public PresenceService(Clock clock) {
        this(clock, 60, 5);
    }

    public PresenceService() {
        this(Clock.systemUTC(), 60, 5);
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return clock;
    }

    public Duration getHeartbeatExpiry() {
        return heartbeatExpiry;
    }

    public void setHeartbeatExpiry(Duration heartbeatExpiry) {
        this.heartbeatExpiry = heartbeatExpiry;
    }

    public Duration getLeaveGrace() {
        return leaveGrace;
    }

    public void setLeaveGrace(Duration leaveGrace) {
        this.leaveGrace = leaveGrace;
    }

    public Object getRoomLock(UUID roomId) {
        return roomLocks.computeIfAbsent(roomId, k -> new Object());
    }

    public void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank() || clientId.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid clientId");
        }
    }

    public void heartbeat(UUID roomId, Long userId, String clientId, boolean isOwner) {
        validateClientId(clientId);
        Instant now = clock.instant();

        synchronized (getRoomLock(roomId)) {
            if (isOwner) {
                seenOwners.put(roomId, Boolean.TRUE);
            }

            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap =
                    roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
            ConcurrentHashMap<String, ClientSession> clientMap =
                    userMap.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

            if (!clientMap.containsKey(clientId) && clientMap.size() >= MAX_CLIENTS_PER_USER) {
                clientMap.entrySet().removeIf(entry -> !entry.getValue().isActive(now));
                if (clientMap.size() >= MAX_CLIENTS_PER_USER) {
                    String oldestKey = clientMap.entrySet().stream()
                            .min(Comparator.comparing(e -> e.getValue().getLastHeartbeat()))
                            .map(Map.Entry::getKey)
                            .orElse(null);
                    if (oldestKey != null) {
                        clientMap.remove(oldestKey);
                    }
                }
            }

            ClientSession session = clientMap.computeIfAbsent(clientId, k -> new ClientSession(now, heartbeatExpiry, leaveGrace));
            session.setLastHeartbeat(now);
            session.setLeaveStartedAt(null);
        }
    }

    public void recordSocketOpen(UUID roomId, Long userId, String clientId, boolean isOwner) {
        heartbeat(roomId, userId, clientId, isOwner);
        synchronized (getRoomLock(roomId)) {
            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
            if (userMap != null) {
                ConcurrentHashMap<String, ClientSession> clientMap = userMap.get(userId);
                if (clientMap != null) {
                    ClientSession session = clientMap.get(clientId);
                    if (session != null) {
                        session.setHasActiveSocket(true);
                        session.setLeaveStartedAt(null);
                    }
                }
            }
        }
    }

    public void recordSocketPong(UUID roomId, Long userId, String clientId, boolean isOwner) {
        Instant now = clock.instant();
        synchronized (getRoomLock(roomId)) {
            if (isOwner) {
                seenOwners.put(roomId, Boolean.TRUE);
            }
            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap =
                    roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
            ConcurrentHashMap<String, ClientSession> clientMap =
                    userMap.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
            ClientSession session = clientMap.computeIfAbsent(clientId, k -> new ClientSession(now, heartbeatExpiry, leaveGrace));
            session.setLastHeartbeat(now);
            session.setHasActiveSocket(true);
            session.setLeaveStartedAt(null);
        }
    }

    public void recordSocketClose(UUID roomId, Long userId, String clientId, boolean isOwner) {
        Instant now = clock.instant();
        synchronized (getRoomLock(roomId)) {
            if (isOwner) {
                seenOwners.put(roomId, Boolean.TRUE);
            }
            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
            if (userMap != null) {
                ConcurrentHashMap<String, ClientSession> clientMap = userMap.get(userId);
                if (clientMap != null) {
                    ClientSession session = clientMap.get(clientId);
                    if (session != null) {
                        session.setHasActiveSocket(false);
                        session.setLeaveStartedAt(now);
                    } else {
                        ClientSession newSession = new ClientSession(now, heartbeatExpiry, leaveGrace);
                        newSession.setHasActiveSocket(false);
                        newSession.setLeaveStartedAt(now);
                        clientMap.put(clientId, newSession);
                    }
                }
            }
        }
    }

    public void leave(UUID roomId, Long userId, String clientId, boolean isOwner) {
        validateClientId(clientId);
        Instant now = clock.instant();

        synchronized (getRoomLock(roomId)) {
            if (isOwner) {
                seenOwners.put(roomId, Boolean.TRUE);
            }

            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
            if (userMap != null) {
                ConcurrentHashMap<String, ClientSession> clientMap = userMap.get(userId);
                if (clientMap != null) {
                    ClientSession session = clientMap.get(clientId);
                    if (session != null) {
                        session.setHasActiveSocket(false);
                        session.setLeaveStartedAt(now);
                    } else {
                        ClientSession newSession = new ClientSession(now, heartbeatExpiry, leaveGrace);
                        newSession.setHasActiveSocket(false);
                        newSession.setLeaveStartedAt(now);
                        clientMap.put(clientId, newSession);
                    }
                }
            }
        }
    }

    public boolean isUserActive(UUID roomId, Long userId) {
        Instant now = clock.instant();
        ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
        if (userMap == null) return false;
        ConcurrentHashMap<String, ClientSession> clientMap = userMap.get(userId);
        if (clientMap == null || clientMap.isEmpty()) return false;

        return clientMap.values().stream().anyMatch(s -> s.isActive(now));
    }

    public boolean isRoomOffline(UUID roomId, Long ownerId) {
        // A room with no recorded owner heartbeat since server start counts as ONLINE.
        if (!seenOwners.containsKey(roomId)) {
            return false;
        }
        // Room goes offline only after the owner was seen and then expired or left.
        return !isUserActive(roomId, ownerId);
    }

    public List<ExpiredMember> computeExpiredMembers() {
        Instant now = clock.instant();
        List<ExpiredMember> expired = new ArrayList<>();

        for (Map.Entry<UUID, ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>>> roomEntry : roomSessions.entrySet()) {
            UUID roomId = roomEntry.getKey();
            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomEntry.getValue();

            synchronized (getRoomLock(roomId)) {
                for (Map.Entry<Long, ConcurrentHashMap<String, ClientSession>> userEntry : userMap.entrySet()) {
                    Long userId = userEntry.getKey();
                    ConcurrentHashMap<String, ClientSession> clientMap = userEntry.getValue();

                    boolean hasActive = clientMap.values().stream().anyMatch(s -> s.isActive(now));
                    if (!hasActive) {
                        expired.add(new ExpiredMember(roomId, userId));
                    }
                }
            }
        }

        return expired;
    }

    public void removeUser(UUID roomId, Long userId) {
        synchronized (getRoomLock(roomId)) {
            ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
            if (userMap != null) {
                userMap.remove(userId);
                if (userMap.isEmpty()) {
                    roomSessions.remove(roomId);
                    seenOwners.remove(roomId);
                    roomLocks.remove(roomId);
                }
            }
        }
    }

    public void clearRoom(UUID roomId) {
        synchronized (getRoomLock(roomId)) {
            roomSessions.remove(roomId);
            seenOwners.remove(roomId);
            roomLocks.remove(roomId);
        }
    }

    public void cleanupEmptyRooms() {
        for (UUID roomId : new ArrayList<>(roomSessions.keySet())) {
            synchronized (getRoomLock(roomId)) {
                ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>> userMap = roomSessions.get(roomId);
                if (userMap == null || userMap.isEmpty()) {
                    roomSessions.remove(roomId);
                    seenOwners.remove(roomId);
                    roomLocks.remove(roomId);
                }
            }
        }
    }

    public void reset() {
        roomSessions.clear();
        seenOwners.clear();
        roomLocks.clear();
    }
}
