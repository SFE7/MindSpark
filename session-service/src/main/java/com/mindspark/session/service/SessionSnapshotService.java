package com.mindspark.session.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mindspark.session.client.BackendRoomClient;
import com.mindspark.session.dto.BackendIdeaDto;
import com.mindspark.session.dto.BackendParticipantDto;
import com.mindspark.session.dto.BackendRoomExportDto;
import com.mindspark.session.dto.CreateSnapshotRequest;
import com.mindspark.session.dto.SnapshotDetailResponse;
import com.mindspark.session.dto.SnapshotSummaryResponse;
import com.mindspark.session.entity.SessionSnapshot;
import com.mindspark.session.exception.ForbiddenAccessException;
import com.mindspark.session.exception.SnapshotNotFoundException;
import com.mindspark.session.repository.SessionSnapshotRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@Transactional
public class SessionSnapshotService {

    private final SessionSnapshotRepository repository;
    private final BackendRoomClient backendRoomClient;
    private final SessionExportService exportService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("UTC"));

    public SessionSnapshotService(
            SessionSnapshotRepository repository,
            BackendRoomClient backendRoomClient,
            SessionExportService exportService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.backendRoomClient = backendRoomClient;
        this.exportService = exportService;
        this.objectMapper = objectMapper;
    }

    public SnapshotDetailResponse createSnapshot(String sub, String username, boolean isAdmin, CreateSnapshotRequest req) {
        BackendRoomExportDto roomData = backendRoomClient.fetchRoomExportData(req.roomId());

        // Zero IDOR membership check
        boolean isOwner = roomData.ownerKeycloakId() != null && roomData.ownerKeycloakId().equals(sub);
        boolean isParticipant = roomData.participants() != null && roomData.participants().stream()
                .anyMatch(p -> p.keycloakId() != null && p.keycloakId().equals(sub));

        if (!isAdmin && !isOwner && !isParticipant) {
            log.warn("IDOR prevention: User {} attempted to save room {} without membership", sub, req.roomId());
            throw new ForbiddenAccessException("You are not a member of this room");
        }

        int ideaCount = roomData.ideas() != null ? roomData.ideas().size() : 0;
        int categoryCount = roomData.categories() != null ? roomData.categories().size() : 0;
        int totalVotes = roomData.ideas() != null ? roomData.ideas().stream().mapToInt(BackendIdeaDto::voteCount).sum() : 0;
        int participantCount = roomData.participants() != null ? roomData.participants().size() : 0;

        List<String> subList = new ArrayList<>();
        if (roomData.ownerKeycloakId() != null) {
            subList.add(roomData.ownerKeycloakId());
        }
        if (roomData.participants() != null) {
            for (BackendParticipantDto p : roomData.participants()) {
                if (p.keycloakId() != null && !subList.contains(p.keycloakId())) {
                    subList.add(p.keycloakId());
                }
            }
        }

        String participantUserIdsJson;
        String snapshotPayloadJson;
        try {
            participantUserIdsJson = objectMapper.writeValueAsString(subList);
            snapshotPayloadJson = objectMapper.writeValueAsString(roomData);
        } catch (Exception e) {
            log.error("Failed to serialize snapshot payload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize session snapshot payload", e);
        }

        String title = (req.title() != null && !req.title().isBlank())
                ? req.title().trim()
                : (roomData.roomName() + " — " + DATE_FMT.format(Instant.now()));

        SessionSnapshot snapshot = SessionSnapshot.builder()
                .id(UUID.randomUUID())
                .roomId(roomData.roomId().toString())
                .roomName(roomData.roomName())
                .invitationCode(roomData.invitationCode())
                .title(title)
                .notes(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : null)
                .aiSummary(req.aiSummary() != null && !req.aiSummary().isBlank() ? req.aiSummary().trim() : null)
                .savedByUserId(sub)
                .savedByName(username != null ? username : sub)
                .ownerUserId(roomData.ownerKeycloakId() != null ? roomData.ownerKeycloakId() : "")
                .participantUserIds(participantUserIdsJson)
                .ideaCount(ideaCount)
                .categoryCount(categoryCount)
                .totalVotes(totalVotes)
                .participantCount(participantCount)
                .snapshotPayload(snapshotPayloadJson)
                .createdAt(Instant.now())
                .build();

        SessionSnapshot saved = repository.save(snapshot);

        return toDetailResponse(saved, roomData);
    }

    @Transactional(readOnly = true)
    public List<SnapshotSummaryResponse> listSnapshots(String sub, boolean isAdmin, String roomId, String search) {
        String cleanRoomId = (roomId != null && !roomId.isBlank()) ? roomId.trim() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;

        List<SessionSnapshot> snapshots;
        if (isAdmin) {
            snapshots = repository.findAllSnapshotsAdmin(cleanRoomId, searchPattern);
        } else {
            String participantPattern = "%\"" + sub + "\"%";
            snapshots = repository.findAccessibleSnapshots(sub, participantPattern, cleanRoomId, searchPattern);
        }

        return snapshots.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SnapshotDetailResponse getSnapshot(UUID id, String sub, boolean isAdmin) {
        SessionSnapshot snapshot = repository.findById(id)
                .orElseThrow(() -> new SnapshotNotFoundException("Snapshot not found: " + id));

        verifySnapshotAccess(snapshot, sub, isAdmin);

        BackendRoomExportDto roomData = parsePayload(snapshot.getSnapshotPayload());
        return toDetailResponse(snapshot, roomData);
    }

    public void deleteSnapshot(UUID id, String sub, boolean isAdmin) {
        SessionSnapshot snapshot = repository.findById(id)
                .orElseThrow(() -> new SnapshotNotFoundException("Snapshot not found: " + id));

        boolean canDelete = isAdmin
                || (snapshot.getSavedByUserId() != null && snapshot.getSavedByUserId().equals(sub))
                || (snapshot.getOwnerUserId() != null && snapshot.getOwnerUserId().equals(sub));

        if (!canDelete) {
            log.warn("IDOR prevention: User {} attempted to delete snapshot {} owned by {}", sub, id, snapshot.getSavedByUserId());
            throw new ForbiddenAccessException("You do not have permission to delete this snapshot");
        }

        repository.delete(snapshot);
    }

    @Transactional(readOnly = true)
    public SessionExportService.ExportResult exportSnapshot(UUID id, String sub, boolean isAdmin, String format) {
        SessionSnapshot snapshot = repository.findById(id)
                .orElseThrow(() -> new SnapshotNotFoundException("Snapshot not found: " + id));

        verifySnapshotAccess(snapshot, sub, isAdmin);

        BackendRoomExportDto roomData = parsePayload(snapshot.getSnapshotPayload());
        return exportService.renderExport(
                snapshot.getTitle(),
                snapshot.getNotes(),
                snapshot.getAiSummary(),
                roomData,
                format
        );
    }

    @Transactional(readOnly = true)
    public SessionExportService.ExportResult exportLiveRoom(String roomId, String sub, boolean isAdmin, String format) {
        BackendRoomExportDto roomData = backendRoomClient.fetchRoomExportData(roomId);

        boolean isOwner = roomData.ownerKeycloakId() != null && roomData.ownerKeycloakId().equals(sub);
        boolean isParticipant = roomData.participants() != null && roomData.participants().stream()
                .anyMatch(p -> p.keycloakId() != null && p.keycloakId().equals(sub));

        if (!isAdmin && !isOwner && !isParticipant) {
            log.warn("IDOR prevention: User {} attempted to export live room {} without membership", sub, roomId);
            throw new ForbiddenAccessException("You are not a member of this room");
        }

        return exportService.renderExport(
                roomData.roomName(),
                null,
                null,
                roomData,
                format
        );
    }

    private void verifySnapshotAccess(SessionSnapshot snapshot, String sub, boolean isAdmin) {
        if (isAdmin) return;

        boolean isSavedBy = snapshot.getSavedByUserId() != null && snapshot.getSavedByUserId().equals(sub);
        boolean isOwner = snapshot.getOwnerUserId() != null && snapshot.getOwnerUserId().equals(sub);
        boolean isParticipant = isSubInParticipants(sub, snapshot.getParticipantUserIds());

        if (!isSavedBy && !isOwner && !isParticipant) {
            log.warn("IDOR prevention: User {} attempted to access snapshot {}", sub, snapshot.getId());
            throw new ForbiddenAccessException("You do not have access to this session snapshot");
        }
    }

    private boolean isSubInParticipants(String sub, String participantUserIdsJson) {
        if (sub == null || participantUserIdsJson == null || participantUserIdsJson.isBlank()) {
            return false;
        }
        try {
            List<String> list = objectMapper.readValue(participantUserIdsJson, new TypeReference<List<String>>() {});
            return list != null && list.contains(sub);
        } catch (Exception e) {
            // Fallback substring check
            return participantUserIdsJson.contains("\"" + sub + "\"");
        }
    }

    private BackendRoomExportDto parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, BackendRoomExportDto.class);
        } catch (Exception e) {
            log.error("Failed to parse snapshot payload JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read snapshot room data", e);
        }
    }

    private SnapshotSummaryResponse toSummaryResponse(SessionSnapshot s) {
        return new SnapshotSummaryResponse(
                s.getId(),
                s.getRoomId(),
                s.getRoomName(),
                s.getInvitationCode(),
                s.getTitle(),
                s.getNotes(),
                s.getSavedByUserId(),
                s.getSavedByName(),
                s.getOwnerUserId(),
                s.getIdeaCount() != null ? s.getIdeaCount() : 0,
                s.getCategoryCount() != null ? s.getCategoryCount() : 0,
                s.getTotalVotes() != null ? s.getTotalVotes() : 0,
                s.getParticipantCount() != null ? s.getParticipantCount() : 0,
                s.getCreatedAt(),
                s.getAiSummary() != null && !s.getAiSummary().isBlank()
        );
    }

    private SnapshotDetailResponse toDetailResponse(SessionSnapshot s, BackendRoomExportDto roomData) {
        return new SnapshotDetailResponse(
                s.getId(),
                s.getRoomId(),
                s.getRoomName(),
                s.getInvitationCode(),
                s.getTitle(),
                s.getNotes(),
                s.getAiSummary(),
                s.getSavedByUserId(),
                s.getSavedByName(),
                s.getOwnerUserId(),
                s.getIdeaCount() != null ? s.getIdeaCount() : 0,
                s.getCategoryCount() != null ? s.getCategoryCount() : 0,
                s.getTotalVotes() != null ? s.getTotalVotes() : 0,
                s.getParticipantCount() != null ? s.getParticipantCount() : 0,
                s.getCreatedAt(),
                roomData
        );
    }
}
