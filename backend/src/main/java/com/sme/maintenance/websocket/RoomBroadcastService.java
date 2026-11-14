package com.sme.maintenance.websocket;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;

@Service
public class RoomBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(RoomBroadcastService.class);

    public record SocketBinding(WebSocketSession session, Long userId, String clientId) {}

    private final RoomRepository roomRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final ConcurrentHashMap<UUID, AtomicLong> roomSequences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SocketBinding>> roomSessions = new ConcurrentHashMap<>();

    @Autowired
    public RoomBroadcastService(RoomRepository roomRepository,
                                @Autowired(required = false) ObjectMapper objectMapper,
                                @Autowired(required = false) Clock clock) {
        this.roomRepository = roomRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public long nextSequence(UUID roomId) {
        return roomSequences.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public long getCurrentSequence(UUID roomId) {
        AtomicLong seq = roomSequences.get(roomId);
        return seq != null ? seq.get() : 0L;
    }

    public Clock getClock() {
        return clock;
    }

    public void registerSession(UUID roomId, Long userId, String clientId, WebSocketSession session) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        bindings.addIfAbsent(new SocketBinding(session, userId, clientId));
        log.info("Registered WebSocket session for room {} and user {}", roomId, userId);
    }

    public void unregisterSession(UUID roomId, WebSocketSession session) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.get(roomId);
        if (bindings != null) {
            bindings.removeIf(b -> Objects.equals(b.session().getId(), session.getId()));
            if (bindings.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
    }

    public int getSessionCount(UUID roomId, Long userId) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.get(roomId);
        if (bindings == null) return 0;
        int count = 0;
        for (SocketBinding b : bindings) {
            if (Objects.equals(b.userId(), userId)) {
                count++;
            }
        }
        return count;
    }

    public SocketBinding getOldestSession(UUID roomId, Long userId) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.get(roomId);
        if (bindings == null) return null;
        for (SocketBinding b : bindings) {
            if (Objects.equals(b.userId(), userId)) {
                return b;
            }
        }
        return null;
    }

    public void closeUserSockets(UUID roomId, Long userId, int code, String reason) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.get(roomId);
        if (bindings == null) return;

        CloseStatus status = new CloseStatus(code, reason != null ? reason : "Closed");
        for (SocketBinding binding : bindings) {
            if (Objects.equals(binding.userId(), userId)) {
                try {
                    if (binding.session().isOpen()) {
                        binding.session().close(status);
                    }
                } catch (IOException e) {
                    log.warn("Failed to close socket for user {} in room {}: {}", userId, roomId, e.getMessage());
                }
            }
        }
        bindings.removeIf(b -> Objects.equals(b.userId(), userId));
    }

    public void closeRoomSockets(UUID roomId, int code, String reason) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.remove(roomId);
        if (bindings == null) return;

        CloseStatus status = new CloseStatus(code, reason != null ? reason : "Room closed");
        for (SocketBinding binding : bindings) {
            try {
                if (binding.session().isOpen()) {
                    binding.session().close(status);
                }
            } catch (IOException e) {
                log.warn("Failed to close socket in room {}: {}", roomId, e.getMessage());
            }
        }
    }

    public void broadcast(UUID roomId, RoomEvent event) {
        CopyOnWriteArrayList<SocketBinding> bindings = roomSessions.get(roomId);
        if (bindings == null || bindings.isEmpty()) {
            return;
        }

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            closeRoomSockets(roomId, 4404, "Room deleted");
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize RoomEvent for room {}", roomId, e);
            return;
        }
        TextMessage message = new TextMessage(payload);

        for (SocketBinding binding : bindings) {
            WebSocketSession session = binding.session();
            if (!session.isOpen()) {
                bindings.remove(binding);
                continue;
            }

            // Membership check at send time (Amendment 2)
            Long userId = binding.userId();
            boolean isMember = room.getOwnerId().equals(userId) || room.getMemberIds().contains(userId);
            if (!isMember) {
                try {
                    session.close(new CloseStatus(4403, "Removed from room"));
                } catch (IOException ex) {
                    log.warn("Error closing non-member socket {}: {}", session.getId(), ex.getMessage());
                }
                bindings.remove(binding);
                continue; // Do NOT send event to removed member
            }

            try {
                session.sendMessage(message);
            } catch (Exception ex) {
                log.warn("Failed to send WebSocket message to session {} in room {}: {}",
                        session.getId(), roomId, ex.getMessage());
                bindings.remove(binding);
            }
        }
    }

    public void broadcastAfterCommit(UUID roomId, Supplier<RoomEvent> eventSupplier) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        RoomEvent event = eventSupplier.get();
                        if (event != null) {
                            broadcast(roomId, event);
                        }
                    } catch (Exception e) {
                        log.error("Failed to broadcast event after commit for room {}", roomId, e);
                    }
                }
            });
        } else {
            try {
                RoomEvent event = eventSupplier.get();
                if (event != null) {
                    broadcast(roomId, event);
                }
            } catch (Exception e) {
                log.error("Failed to broadcast event immediately for room {}", roomId, e);
            }
        }
    }
}
