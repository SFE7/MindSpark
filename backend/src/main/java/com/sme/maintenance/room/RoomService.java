package com.sme.maintenance.room;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.kafka.KafkaEventProducer;
import com.sme.maintenance.kafka.NoOpKafkaEventProducer;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.subscription.EntitlementDto;
import com.sme.maintenance.subscription.ParticipantLimitReachedException;
import com.sme.maintenance.subscription.RoomLimitReachedException;
import com.sme.maintenance.vote.VoteRepository;

@Service
@Transactional
public class RoomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final PresenceService presenceService;
    private final TransactionTemplate transactionTemplate;
    private final Map<Long, Object> inMemoryUserLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> inMemoryRoomLocks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventProducer kafkaEventProducer = new NoOpKafkaEventProducer();

    public void setKafkaEventProducer(KafkaEventProducer kafkaEventProducer) {
        this.kafkaEventProducer = kafkaEventProducer != null ? kafkaEventProducer : new NoOpKafkaEventProducer();
    }

    public RoomService(
            RoomRepository roomRepository,
            UserRepository userRepository,
            IdeaRepository ideaRepository,
            VoteRepository voteRepository,
            PresenceService presenceService) {
        this(roomRepository, userRepository, ideaRepository, voteRepository, presenceService, (PlatformTransactionManager) null);
    }

    @Autowired
    public RoomService(
            RoomRepository roomRepository,
            UserRepository userRepository,
            IdeaRepository ideaRepository,
            VoteRepository voteRepository,
            PresenceService presenceService,
            @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.ideaRepository = ideaRepository;
        this.voteRepository = voteRepository;
        this.presenceService = presenceService;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    // i can create a room
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Room create(String name, Long userId) {
        return create(name, userId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Room create(String name, Long userId, EntitlementDto limits) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name is required");
        }

        synchronized (getInMemoryLock(userId)) {
            if (transactionTemplate != null) {
                return transactionTemplate.execute(status -> doCreate(name, userId, limits));
            } else {
                return doCreate(name, userId, limits);
            }
        }
    }

    private Room doCreate(String name, Long userId, EntitlementDto limits) {
        User user = getUser(userId);
        acquireCreatorLock(userId);

        if (limits != null) {
            long activeRooms = roomRepository.countByOwnerAndClosedFalse(user);
            if (activeRooms >= limits.maxActiveRooms()) {
                throw new RoomLimitReachedException(limits.planCode(), limits.maxActiveRooms(), (int) activeRooms);
            }
        }

        String code;
        do {
            code = generateCode();
        } while (roomRepository.findByInvitationCode(code).isPresent());
        return roomRepository.save(new Room(name, code, user));
    }

    public Room update(UUID roomId, String name, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);
        requireOpen(room);

        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name is required");
        }

        room.setName(name.trim());
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> ownedBy(Long userId) {
        return roomRepository.findByOwnerOrderByCreatedAtDesc(getUser(userId));
    }

    @Transactional(readOnly = true)
    public Room findForJoin(String invitationCode) {
        if (invitationCode == null || invitationCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation code is required");
        }
        Room room = roomRepository.findByInvitationCode(invitationCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join a closed room");
        }
        return room;
    }

    private Object getInMemoryRoomLock(String code) {
        return inMemoryRoomLocks.computeIfAbsent(code, k -> new Object());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Room join(String invitationCode, Long userId) {
        return joinWithLimits(invitationCode, userId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Room joinWithLimits(String invitationCode, Long userId, EntitlementDto ownerLimits) {
        if (invitationCode == null || invitationCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation code is required");
        }
        String normalizedCode = invitationCode.trim().toUpperCase();
        getUser(userId);

        synchronized (getInMemoryRoomLock(normalizedCode)) {
            if (transactionTemplate != null) {
                return transactionTemplate.execute(status -> doJoinWithLimits(normalizedCode, userId, ownerLimits));
            } else {
                return doJoinWithLimits(normalizedCode, userId, ownerLimits);
            }
        }
    }

    private Room doJoinWithLimits(String normalizedCode, Long userId, EntitlementDto ownerLimits) {
        Room room = roomRepository.findByInvitationCodeWithLock(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join a closed room");
        }

        // Idempotent re-join: if already a member or owner, succeed immediately
        if (room.getMemberIds().contains(userId) || room.getOwnerId().equals(userId)) {
            return room;
        }

        if (ownerLimits != null && room.getMemberIds().size() >= ownerLimits.maxParticipantsPerRoom()) {
            throw new ParticipantLimitReachedException(ownerLimits.maxParticipantsPerRoom(), room.getMemberIds().size());
        }

        room.getMemberIds().add(userId);
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public long countActiveRooms(Long userId) {
        User user = getUser(userId);
        return roomRepository.countByOwnerAndClosedFalse(user);
    }

    public Room close(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);
        room.setClosed(true);
        Room saved = roomRepository.save(room);

        List<String> recipientSubs = getRoomRecipientSubs(saved);
        kafkaEventProducer.publishRoomClosed(saved, recipientSubs);

        return saved;
    }

    private List<String> getRoomRecipientSubs(Room room) {
        java.util.Set<String> subs = new java.util.LinkedHashSet<>();
        if (room.getOwner() != null && room.getOwner().getKeycloakId() != null && !room.getOwner().getKeycloakId().isBlank()) {
            subs.add(room.getOwner().getKeycloakId());
        }
        if (room.getMemberIds() != null && !room.getMemberIds().isEmpty()) {
            List<User> members = userRepository.findAllById(room.getMemberIds());
            for (User member : members) {
                if (member.getKeycloakId() != null && !member.getKeycloakId().isBlank()) {
                    subs.add(member.getKeycloakId());
                }
            }
        }
        return new java.util.ArrayList<>(subs);
    }

    public Room open(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);
        room.setClosed(false);
        return roomRepository.save(room);
    }

    public void delete(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);

        if (presenceService != null) {
            presenceService.clearRoom(roomId);
        }

        voteRepository.deleteByRoom(room);
        ideaRepository.deleteByRoom(room);
        room.getMemberIds().clear();
        roomRepository.delete(room);
    }

    public Room reopen(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);
        room.getMemberIds().add(userId);
        return roomRepository.save(room);
    }
//As a client, I can leave a brainstorming room.
    public Room leave(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireMember(room, userId);
        room.getMemberIds().remove(userId);
        return roomRepository.save(room);
    }

    @Transactional
    public Room removeMember(UUID roomId, Long memberIdToRemove, Long requesterUserId) {
        Room room = find(roomId);
        requireOwner(room, requesterUserId);

        if (memberIdToRemove == null || memberIdToRemove.equals(room.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creator cannot remove themselves");
        }

        if (!room.getMemberIds().contains(memberIdToRemove)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target is not a member of this room");
        }

        room.getMemberIds().remove(memberIdToRemove);
        return roomRepository.save(room);
    }

    public Room memberRoom(UUID roomId, Long userId) {
        Room room = find(roomId);
        if (room.getOwnerId() != null && room.getOwnerId().equals(userId) && !room.getMemberIds().contains(userId)) {
            room.getMemberIds().add(userId);
            return roomRepository.save(room);
        }
        requireMember(room, userId);
        return room;
    }

    public Room find(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    @Transactional(readOnly = true)
    public List<User> members(UUID roomId) {
        Room room = find(roomId);
        return userRepository.findAllById(room.getMemberIds());
    }

    public void requireMember(Room room, Long userId) {
        if (!room.getMemberIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room first");
        }
    }

    public void requireOpen(Room room) {
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This room is closed");
        }
        if (presenceService != null && presenceService.isRoomOffline(room.getId(), room.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Room offline: the owner is away");
        }
    }

    public void requireOwner(Room room, Long userId) { //Ilyes: i changeed the visiblty to public since i need to use it inside the category service
        if (!room.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room owner can do this");

        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void acquireCreatorLock(Long userId) {
        if (jdbcTemplate != null) {
            try {
                String dbProductName = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<String>) conn ->
                        conn.getMetaData().getDatabaseProductName()
                );
                if (dbProductName != null && dbProductName.toLowerCase().contains("postgres")) {
                    jdbcTemplate.queryForObject(
                            "SELECT pg_advisory_xact_lock(hashtext(?))",
                            Object.class,
                            "user-room:" + userId
                    );
                }
            } catch (Exception e) {
                // Non-Postgres fallback or lock handling
            }
        }
    }

    private Object getInMemoryLock(Long userId) {
        return inMemoryUserLocks.computeIfAbsent(userId, k -> new Object());
    }
}