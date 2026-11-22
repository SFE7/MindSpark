package com.sme.maintenance.votepool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.kafka.KafkaEventProducer;
import com.sme.maintenance.kafka.NoOpKafkaEventProducer;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.votepool.dto.CastVoteRequest;
import com.sme.maintenance.votepool.dto.CreateVotePoolRequest;
import com.sme.maintenance.votepool.dto.VotePoolResponse;
import com.sme.maintenance.votepool.dto.VotePoolTallyDto;
import com.sme.maintenance.websocket.RoomWebSocketHandler;

import java.sql.Connection;
import java.util.Locale;
import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;

@Service
public class VotePoolService {

    private static final Logger log = LoggerFactory.getLogger(VotePoolService.class);

    private final VotePoolRepository votePoolRepository;
    private final VotePoolVoteRepository votePoolVoteRepository;
    private final RoomRepository roomRepository;
    private final IdeaRepository ideaRepository;
    private final RoomWebSocketHandler webSocketHandler;
    private final DataSource dataSource;

    @Autowired(required = false)
    private KafkaEventProducer kafkaEventProducer = new NoOpKafkaEventProducer();

    @Autowired(required = false)
    private UserRepository userRepository;

    public void setKafkaEventProducer(KafkaEventProducer kafkaEventProducer) {
        this.kafkaEventProducer = kafkaEventProducer != null ? kafkaEventProducer : new NoOpKafkaEventProducer();
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private volatile boolean isPostgres = false;

    private static final int STRIPE_COUNT = 128;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPE_COUNT];

    public VotePoolService(
            VotePoolRepository votePoolRepository,
            VotePoolVoteRepository votePoolVoteRepository,
            RoomRepository roomRepository,
            IdeaRepository ideaRepository,
            RoomWebSocketHandler webSocketHandler,
            DataSource dataSource) {
        this.votePoolRepository = votePoolRepository;
        this.votePoolVoteRepository = votePoolVoteRepository;
        this.roomRepository = roomRepository;
        this.ideaRepository = ideaRepository;
        this.webSocketHandler = webSocketHandler;
        this.dataSource = dataSource;
        for (int i = 0; i < STRIPE_COUNT; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @PostConstruct
    public void detectDatabaseType() {
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                String dbName = conn.getMetaData().getDatabaseProductName();
                if (dbName != null && dbName.toLowerCase(Locale.ROOT).contains("postgres")) {
                    isPostgres = true;
                }
            } catch (Exception e) {
                log.debug("Database metadata lookup: {}", e.getMessage());
            }
        }
    }

    private ReentrantLock getLock(UUID poolId, Long userId) {
        int hash = Objects.hash(poolId, userId);
        int index = Math.abs(hash % STRIPE_COUNT);
        return locks[index];
    }

    private void tryPostgresAdvisoryLock(UUID poolId, Long userId) {
        if (!isPostgres) {
            return;
        }
        try {
            String lockKey = (poolId != null ? poolId.toString() : "") + ":" + (userId != null ? userId.toString() : "");
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
                    .setParameter("lockKey", lockKey)
                    .getSingleResult();
        } catch (Exception e) {
            log.trace("Advisory lock failed: {}", e.getMessage());
        }
    }

    public boolean isRoomOwner(Room room, User user) {
        if (room == null || user == null) return false;
        if (room.getOwner() != null && room.getOwner().getId().equals(user.getId())) return true;
        return room.getOwner() != null && room.getOwner().getKeycloakId() != null
                && room.getOwner().getKeycloakId().equals(user.getKeycloakId());
    }

    public boolean isRoomMember(Room room, User user) {
        if (room == null || user == null) return false;
        if (isRoomOwner(room, user)) return true;
        return room.getMemberIds() != null && room.getMemberIds().contains(user.getId());
    }

    @Transactional
    public VotePoolResponse createVotePool(UUID roomId, CreateVotePoolRequest request, User caller) {
        Room room = roomRepository.findByIdWithLock(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomOwner(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_OWNER");
        }

        Optional<VotePool> active = votePoolRepository.findActivePoolByRoomId(roomId);
        if (active.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE_VOTE_POOL_EXISTS");
        }

        int votes = request.votesPerParticipant() != null ? request.votesPerParticipant() : 3;
        boolean allowMultiple = request.allowMultipleVotesPerIdea() != null ? request.allowMultipleVotesPerIdea() : true;

        VotePool pool = new VotePool(room, request.title(), votes, allowMultiple, caller);
        VotePool saved = votePoolRepository.save(pool);

        VotePoolResponse response = buildResponse(saved, caller);
        broadcastVotePoolUpdated(roomId, saved);
        return response;
    }

    @Transactional(readOnly = true)
    public VotePoolResponse getActiveOrLatestPool(UUID roomId, boolean includeLatestClosed, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        Optional<VotePool> active = votePoolRepository.findActivePoolByRoomId(roomId);
        if (active.isPresent()) {
            return buildResponse(active.get(), caller);
        }

        if (includeLatestClosed) {
            List<VotePool> closed = votePoolRepository.findLatestClosedPools(roomId);
            if (!closed.isEmpty()) {
                return buildResponse(closed.get(0), caller);
            }
        }

        return null;
    }

    @Transactional
    public VotePoolResponse castVote(UUID roomId, UUID poolId, CastVoteRequest request, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        UUID ideaId = request.ideaId();
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));

        if (!idea.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEA_ROOM_MISMATCH");
        }

        ReentrantLock lock = getLock(poolId, caller.getId());
        lock.lock();
        try {
            tryPostgresAdvisoryLock(poolId, caller.getId());

            VotePool pool = votePoolRepository.findById(poolId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VOTE_POOL_NOT_FOUND"));

            if (pool.getStatus() != VotePoolStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "VOTE_POOL_CLOSED");
            }

            if (!pool.getRoom().getId().equals(roomId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POOL_ROOM_MISMATCH");
            }

            long used = votePoolVoteRepository.countByPoolIdAndUserId(poolId, caller.getId());
            if (used >= pool.getVotesPerParticipant()) {
                throw new VoteBudgetExceededException(0, pool.getVotesPerParticipant());
            }

            if (!pool.isAllowMultipleVotesPerIdea()) {
                List<VotePoolVote> existing = votePoolVoteRepository.findByPoolIdAndUserIdAndIdeaId(poolId, caller.getId(), ideaId);
                if (!existing.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_IDEA_VOTE");
                }
            }

            VotePoolVote vote = new VotePoolVote(pool, idea, caller);
            votePoolVoteRepository.save(vote);

            broadcastVotePoolUpdated(roomId, pool);
            return buildResponse(pool, caller);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public VotePoolResponse removeVote(UUID roomId, UUID poolId, UUID ideaId, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        ReentrantLock lock = getLock(poolId, caller.getId());
        lock.lock();
        try {
            tryPostgresAdvisoryLock(poolId, caller.getId());

            VotePool pool = votePoolRepository.findById(poolId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VOTE_POOL_NOT_FOUND"));

            if (pool.getStatus() != VotePoolStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "VOTE_POOL_CLOSED");
            }

            if (!pool.getRoom().getId().equals(roomId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POOL_ROOM_MISMATCH");
            }

            List<VotePoolVote> userVotesForIdea = votePoolVoteRepository.findByPoolIdAndUserIdAndIdeaId(poolId, caller.getId(), ideaId);
            if (userVotesForIdea.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VOTE_NOT_FOUND");
            }

            // Remove one vote
            votePoolVoteRepository.delete(userVotesForIdea.get(0));

            broadcastVotePoolUpdated(roomId, pool);
            return buildResponse(pool, caller);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public VotePoolResponse closeVotePool(UUID roomId, UUID poolId, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (!isRoomOwner(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_OWNER");
        }

        VotePool pool = votePoolRepository.findById(poolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VOTE_POOL_NOT_FOUND"));

        if (!pool.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POOL_ROOM_MISMATCH");
        }

        if (pool.getStatus() == VotePoolStatus.CLOSED) {
            return buildResponse(pool, caller);
        }

        pool.setStatus(VotePoolStatus.CLOSED);
        pool.setClosedAt(Instant.now());
        VotePool saved = votePoolRepository.save(pool);

        broadcastVotePoolUpdated(roomId, saved);

        try {
            List<Object[]> rawCounts = votePoolVoteRepository.countVotesGroupedByIdeaId(saved.getId());
            Map<UUID, Long> countMap = new HashMap<>();
            long totalVotes = 0L;
            for (Object[] row : rawCounts) {
                long c = ((Number) row[1]).longValue();
                countMap.put((UUID) row[0], c);
                totalVotes += c;
            }

            List<Idea> roomIdeas = ideaRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
            List<VotePoolTallyDto> tallies = new ArrayList<>();
            for (Idea i : roomIdeas) {
                long votes = countMap.getOrDefault(i.getId(), 0L);
                tallies.add(new VotePoolTallyDto(i.getId(), i.getTitle(), votes));
            }
            tallies.sort((a, b) -> Long.compare(b.poolVotes(), a.poolVotes()));
            String topIdeaTitle = tallies.isEmpty() ? "" : tallies.get(0).ideaTitle();

            List<String> recipientSubs = getRoomRecipientSubs(room);
            kafkaEventProducer.publishVotePoolClosed(room, saved, recipientSubs, topIdeaTitle, totalVotes);
        } catch (Exception e) {
            log.warn("Failed to dispatch vote-pool.closed Kafka event: {}", e.getMessage());
        }

        return buildResponse(saved, caller);
    }

    @Transactional
    public VotePoolResponse closePool(UUID roomId, UUID poolId, User caller) {
        return closeVotePool(roomId, poolId, caller);
    }

    private List<String> getRoomRecipientSubs(Room room) {
        java.util.Set<String> subs = new java.util.LinkedHashSet<>();
        if (room.getOwner() != null && room.getOwner().getKeycloakId() != null && !room.getOwner().getKeycloakId().isBlank()) {
            subs.add(room.getOwner().getKeycloakId());
        }
        if (userRepository != null && room.getMemberIds() != null && !room.getMemberIds().isEmpty()) {
            List<User> members = userRepository.findAllById(room.getMemberIds());
            for (User member : members) {
                if (member.getKeycloakId() != null && !member.getKeycloakId().isBlank()) {
                    subs.add(member.getKeycloakId());
                }
            }
        }
        return new java.util.ArrayList<>(subs);
    }

    public VotePoolResponse buildResponse(VotePool pool, User caller) {
        int used = (int) votePoolVoteRepository.countByPoolIdAndUserId(pool.getId(), caller.getId());
        int remaining = Math.max(0, pool.getVotesPerParticipant() - used);

        List<VotePoolVote> myVotes = votePoolVoteRepository.findByPoolIdAndUserId(pool.getId(), caller.getId());
        Map<String, Integer> myVotesByIdeaId = new HashMap<>();
        for (VotePoolVote v : myVotes) {
            String idStr = v.getIdea().getId().toString();
            myVotesByIdeaId.put(idStr, myVotesByIdeaId.getOrDefault(idStr, 0) + 1);
        }

        List<Object[]> rawCounts = votePoolVoteRepository.countVotesGroupedByIdeaId(pool.getId());
        Map<UUID, Long> countMap = new HashMap<>();
        for (Object[] row : rawCounts) {
            countMap.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        List<Idea> roomIdeas = ideaRepository.findByRoomIdOrderByCreatedAtDesc(pool.getRoom().getId());
        List<VotePoolTallyDto> tallies = new ArrayList<>();
        for (Idea i : roomIdeas) {
            long votes = countMap.getOrDefault(i.getId(), 0L);
            tallies.add(new VotePoolTallyDto(i.getId(), i.getTitle(), votes));
        }
        tallies.sort((a, b) -> Long.compare(b.poolVotes(), a.poolVotes()));

        return new VotePoolResponse(
                pool.getId(),
                pool.getRoom().getId(),
                pool.getTitle(),
                pool.getVotesPerParticipant(),
                pool.isAllowMultipleVotesPerIdea(),
                pool.getStatus(),
                pool.getCreatedBy() != null ? pool.getCreatedBy().getId() : null,
                pool.getCreatedBy() != null ? pool.getCreatedBy().getKeycloakId() : null,
                pool.getCreatedBy() != null ? pool.getCreatedBy().getUsername() : null,
                pool.getCreatedAt(),
                pool.getClosedAt(),
                used,
                remaining,
                myVotesByIdeaId,
                tallies
        );
    }

    private void broadcastVotePoolUpdated(UUID roomId, VotePool pool) {
        try {
            List<Object[]> rawCounts = votePoolVoteRepository.countVotesGroupedByIdeaId(pool.getId());
            Map<UUID, Long> countMap = new HashMap<>();
            for (Object[] row : rawCounts) {
                countMap.put((UUID) row[0], ((Number) row[1]).longValue());
            }

            List<Idea> roomIdeas = ideaRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
            List<Map<String, Object>> tallies = new ArrayList<>();
            for (Idea i : roomIdeas) {
                tallies.add(Map.of(
                        "ideaId", i.getId().toString(),
                        "ideaTitle", i.getTitle() != null ? i.getTitle() : "",
                        "poolVotes", countMap.getOrDefault(i.getId(), 0L)
                ));
            }
            tallies.sort((a, b) -> Long.compare(
                    ((Number) b.get("poolVotes")).longValue(),
                    ((Number) a.get("poolVotes")).longValue()
            ));

            Map<String, Object> payload = Map.of(
                    "type", "VOTE_POOL_UPDATED",
                    "poolId", pool.getId().toString(),
                    "status", pool.getStatus().name(),
                    "title", pool.getTitle(),
                    "votesPerParticipant", pool.getVotesPerParticipant(),
                    "allowMultipleVotesPerIdea", pool.isAllowMultipleVotesPerIdea(),
                    "tallies", tallies
            );
            webSocketHandler.broadcastToRoom(roomId, payload, null);
        } catch (Exception e) {
            log.error("Failed to broadcast VOTE_POOL_UPDATED for room {}: {}", roomId, e.getMessage(), e);
        }
    }
}
