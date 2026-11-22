package com.sme.maintenance.media;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.media.dto.CanvasImageDto;
import com.sme.maintenance.media.dto.CreateCanvasImageRequest;
import com.sme.maintenance.media.dto.CreateIdeaAttachmentRequest;
import com.sme.maintenance.media.dto.IdeaAttachmentDto;
import com.sme.maintenance.media.dto.RoomMediaBundleDto;
import com.sme.maintenance.media.dto.UpdateCanvasImageRequest;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.websocket.RoomWebSocketHandler;

@Service
public class RoomMediaService {

    private static final Logger log = LoggerFactory.getLogger(RoomMediaService.class);

    private final IdeaAttachmentRepository ideaAttachmentRepository;
    private final CanvasImageRepository canvasImageRepository;
    private final RoomRepository roomRepository;
    private final IdeaRepository ideaRepository;
    private final RoomWebSocketHandler webSocketHandler;

    public RoomMediaService(
            IdeaAttachmentRepository ideaAttachmentRepository,
            CanvasImageRepository canvasImageRepository,
            RoomRepository roomRepository,
            IdeaRepository ideaRepository,
            RoomWebSocketHandler webSocketHandler) {
        this.ideaAttachmentRepository = ideaAttachmentRepository;
        this.canvasImageRepository = canvasImageRepository;
        this.roomRepository = roomRepository;
        this.ideaRepository = ideaRepository;
        this.webSocketHandler = webSocketHandler;
    }

    private boolean isRoomOwner(Room room, User user) {
        if (room == null || user == null) return false;
        if (room.getOwner() != null && room.getOwner().getId().equals(user.getId())) return true;
        return room.getOwner() != null && room.getOwner().getKeycloakId() != null
                && room.getOwner().getKeycloakId().equals(user.getKeycloakId());
    }

    private boolean isRoomMember(Room room, User user) {
        if (room == null || user == null) return false;
        if (isRoomOwner(room, user)) return true;
        return room.getMemberIds() != null && room.getMemberIds().contains(user.getId());
    }

    @Transactional(readOnly = true)
    public RoomMediaBundleDto getRoomMediaBundle(UUID roomId, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        List<IdeaAttachment> attachments = ideaAttachmentRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        Map<String, List<IdeaAttachmentDto>> attachmentsByIdea = new LinkedHashMap<>();
        for (IdeaAttachment att : attachments) {
            String ideaIdStr = att.getIdea().getId().toString();
            attachmentsByIdea.computeIfAbsent(ideaIdStr, k -> new ArrayList<>()).add(toAttachmentDto(att));
        }

        List<CanvasImage> images = canvasImageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        List<CanvasImageDto> imageDtos = images.stream().map(this::toCanvasImageDto).toList();

        return new RoomMediaBundleDto(attachmentsByIdea, imageDtos);
    }

    @Transactional
    public IdeaAttachmentDto addIdeaAttachment(UUID roomId, UUID ideaId, CreateIdeaAttachmentRequest request, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));

        if (!idea.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEA_ROOM_MISMATCH");
        }

        long count = ideaAttachmentRepository.countByIdeaId(ideaId);
        if (count >= 3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEA_ATTACHMENT_LIMIT_REACHED");
        }

        IdeaAttachment attachment = new IdeaAttachment(
                roomId,
                idea,
                request.mediaId(),
                request.contentType(),
                request.width(),
                request.height(),
                caller
        );
        IdeaAttachment saved = ideaAttachmentRepository.save(attachment);
        IdeaAttachmentDto dto = toAttachmentDto(saved);

        broadcastWebSocket(roomId, Map.of(
                "type", "IDEA_ATTACHMENT_ADDED",
                "attachment", dto
        ));

        return dto;
    }

    @Transactional
    public void deleteIdeaAttachment(UUID roomId, UUID ideaId, UUID attachmentId, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        IdeaAttachment attachment = ideaAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"));

        if (!attachment.getRoomId().equals(roomId) || !attachment.getIdea().getId().equals(ideaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_ROOM_MISMATCH");
        }

        boolean isUploader = attachment.getUploadedBy() != null && attachment.getUploadedBy().getId().equals(caller.getId());
        boolean isIdeaAuthor = attachment.getIdea().getAuthor() != null && attachment.getIdea().getAuthor().getId().equals(caller.getId());
        boolean isOwner = isRoomOwner(room, caller);

        if (!isUploader && !isIdeaAuthor && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "UNAUTHORIZED_DELETE");
        }

        ideaAttachmentRepository.delete(attachment);

        broadcastWebSocket(roomId, Map.of(
                "type", "IDEA_ATTACHMENT_REMOVED",
                "roomId", roomId.toString(),
                "ideaId", ideaId.toString(),
                "attachmentId", attachmentId.toString()
        ));
    }

    @Transactional
    public CanvasImageDto addCanvasImage(UUID roomId, CreateCanvasImageRequest request, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        long count = canvasImageRepository.countByRoomId(roomId);
        if (count >= 15) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CANVAS_IMAGE_LIMIT_REACHED");
        }

        int zIndex = request.zIndex() != null ? request.zIndex() : 0;
        CanvasImage image = new CanvasImage(
                room,
                request.mediaId(),
                request.contentType(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                request.naturalWidth(),
                request.naturalHeight(),
                zIndex,
                caller
        );

        CanvasImage saved = canvasImageRepository.save(image);
        CanvasImageDto dto = toCanvasImageDto(saved);

        broadcastWebSocket(roomId, Map.of(
                "type", "CANVAS_IMAGE_ADDED",
                "image", dto
        ));

        return dto;
    }

    @Transactional
    public CanvasImageDto updateCanvasImage(UUID roomId, UUID imageId, UpdateCanvasImageRequest request, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        CanvasImage image = canvasImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CANVAS_IMAGE_NOT_FOUND"));

        if (!image.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_ROOM_MISMATCH");
        }

        image.setX(request.x());
        image.setY(request.y());
        image.setWidth(request.width());
        image.setHeight(request.height());
        if (request.zIndex() != null) {
            image.setZIndex(request.zIndex());
        }

        CanvasImage saved = canvasImageRepository.save(image);
        CanvasImageDto dto = toCanvasImageDto(saved);

        broadcastWebSocket(roomId, Map.of(
                "type", "CANVAS_IMAGE_UPDATED",
                "image", dto
        ));

        return dto;
    }

    @Transactional
    public void deleteCanvasImage(UUID roomId, UUID imageId, User caller) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));

        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!isRoomMember(room, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER");
        }

        CanvasImage image = canvasImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CANVAS_IMAGE_NOT_FOUND"));

        if (!image.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_ROOM_MISMATCH");
        }

        boolean isUploader = image.getUploadedBy() != null && image.getUploadedBy().getId().equals(caller.getId());
        boolean isOwner = isRoomOwner(room, caller);

        if (!isUploader && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "UNAUTHORIZED_DELETE");
        }

        canvasImageRepository.delete(image);

        broadcastWebSocket(roomId, Map.of(
                "type", "CANVAS_IMAGE_DELETED",
                "roomId", roomId.toString(),
                "imageId", imageId.toString()
        ));
    }

    private void broadcastWebSocket(UUID roomId, Map<String, Object> payload) {
        try {
            webSocketHandler.broadcastToRoom(roomId, payload, null);
        } catch (Exception e) {
            log.error("Failed to broadcast WebSocket payload for room {}: {}", roomId, e.getMessage(), e);
        }
    }

    private IdeaAttachmentDto toAttachmentDto(IdeaAttachment att) {
        String url = "/api/media/rooms/" + att.getRoomId() + "/" + att.getMediaId();
        return new IdeaAttachmentDto(
                att.getId(),
                att.getRoomId(),
                att.getIdea().getId(),
                att.getMediaId(),
                url,
                att.getContentType(),
                att.getWidth(),
                att.getHeight(),
                att.getUploadedBy() != null ? att.getUploadedBy().getId() : null,
                att.getUploadedBy() != null ? att.getUploadedBy().getKeycloakId() : null,
                att.getUploadedBy() != null ? att.getUploadedBy().getUsername() : null,
                att.getCreatedAt()
        );
    }

    private CanvasImageDto toCanvasImageDto(CanvasImage img) {
        String url = "/api/media/rooms/" + img.getRoom().getId() + "/" + img.getMediaId();
        return new CanvasImageDto(
                img.getId(),
                img.getRoom().getId(),
                img.getMediaId(),
                url,
                img.getContentType(),
                img.getX(),
                img.getY(),
                img.getWidth(),
                img.getHeight(),
                img.getNaturalWidth(),
                img.getNaturalHeight(),
                img.getZIndex(),
                img.getUploadedBy() != null ? img.getUploadedBy().getId() : null,
                img.getUploadedBy() != null ? img.getUploadedBy().getKeycloakId() : null,
                img.getUploadedBy() != null ? img.getUploadedBy().getUsername() : null,
                img.getCreatedAt()
        );
    }
}
