package com.sme.maintenance.presence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.websocket.RoomBroadcastService;
import com.sme.maintenance.websocket.RoomEvent;

@Component
public class PresenceSweeper {

    private static final Logger log = LoggerFactory.getLogger(PresenceSweeper.class);

    private final PresenceService presenceService;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final TransactionTemplate transactionTemplate;
    private final RoomBroadcastService broadcastService;
    private final ConcurrentHashMap<UUID, Boolean> roomOfflineStates = new ConcurrentHashMap<>();

    @Autowired
    public PresenceSweeper(PresenceService presenceService,
                           RoomService roomService,
                           RoomRepository roomRepository,
                           PlatformTransactionManager transactionManager,
                           @Autowired(required = false) RoomBroadcastService broadcastService) {
        this.presenceService = presenceService;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.broadcastService = broadcastService;
    }

    public PresenceSweeper(PresenceService presenceService,
                           RoomService roomService,
                           RoomRepository roomRepository,
                           PlatformTransactionManager transactionManager) {
        this(presenceService, roomService, roomRepository, transactionManager, null);
    }

    @Scheduled(fixedDelayString = "${mindspark.presence.sweep-delay-ms:2000}")
    public void sweepExpired() {
        List<PresenceService.ExpiredMember> candidates = presenceService.computeExpiredMembers();
        if (candidates.isEmpty()) {
            presenceService.cleanupEmptyRooms();
            return;
        }

        for (PresenceService.ExpiredMember candidate : candidates) {
            UUID roomId = candidate.roomId();
            Long userId = candidate.userId();

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Room room = roomRepository.findById(roomId).orElse(null);
                    if (room == null) {
                        presenceService.clearRoom(roomId);
                        return;
                    }

                    // The owner is never removed by a disconnect
                    if (room.getOwnerId().equals(userId)) {
                        boolean offline = presenceService.isRoomOffline(roomId, userId);
                        Boolean prev = roomOfflineStates.put(roomId, offline);
                        if (Boolean.TRUE.equals(offline) && (prev == null || !prev)) {
                            if (broadcastService != null) {
                                long seq = broadcastService.nextSequence(roomId);
                                Instant sweepNow = broadcastService.getClock() != null ? broadcastService.getClock().instant() : Instant.now();
                                broadcastService.broadcast(roomId, RoomEvent.roomOffline(roomId, seq, sweepNow, true));
                            }
                        }
                        return;
                    }

                    // Use the SAME code path as the existing "Leave room" action
                    roomService.leave(roomId, userId);
                    presenceService.removeUser(roomId, userId);
                    log.info("Removed expired member {} from room {}", userId, roomId);
                });
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    presenceService.clearRoom(roomId);
                } else if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                    presenceService.removeUser(roomId, userId);
                } else {
                    log.error("Error removing expired member {} from room {}: {}", userId, roomId, ex.getMessage());
                }
            } catch (Exception ex) {
                log.error("Unexpected error removing expired member {} from room {}: {}", userId, roomId, ex.getMessage(), ex);
            }
        }

        presenceService.cleanupEmptyRooms();
    }
}
