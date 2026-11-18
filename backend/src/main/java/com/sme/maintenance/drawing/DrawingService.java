package com.sme.maintenance.drawing;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.api.ApiModels.DrawingMoveRequest;
import com.sme.maintenance.api.ApiModels.DrawingRequest;
import com.sme.maintenance.api.ApiModels.DrawingResponse;
import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.websocket.DrawingChangedEvent;

@Service
public class DrawingService {

    public static final int MAX_DRAWINGS_PER_ROOM = 500;

    private final DrawingRepository drawingRepository;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DrawingService(
            DrawingRepository drawingRepository,
            RoomService roomService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.drawingRepository = drawingRepository;
        this.roomService = roomService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<DrawingResponse> getRoomDrawings(UUID roomId, Long userId) {
        Room room = roomService.memberRoom(roomId, userId);
        return drawingRepository.findByRoomAndDeletedFalseOrderByCreatedAtAsc(room)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DrawingResponse createDrawing(UUID roomId, Long userId, DrawingRequest request) {
        Room room = roomService.memberRoom(roomId, userId);
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Room is closed");
        }

        long currentCount = drawingRepository.countByRoomAndDeletedFalse(room);
        if (currentCount >= MAX_DRAWINGS_PER_ROOM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum drawings limit (500) reached for this room");
        }

        if (request.kind() == null || request.kind().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drawing kind is required");
        }

        if (request.payload() == null || request.payload().isBlank() || request.payload().length() > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drawing payload is invalid");
        }

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        Instant now = Instant.now();

        Drawing drawing = new Drawing(
                id,
                room,
                author,
                request.kind().trim(),
                request.color() != null ? request.color() : "#273b3b",
                request.width() != null ? request.width() : 5.0,
                request.opacity() != null ? request.opacity() : 1.0,
                request.payload(),
                request.offsetX() != null ? request.offsetX() : 0.0,
                request.offsetY() != null ? request.offsetY() : 0.0,
                now,
                now
        );

        drawingRepository.save(drawing);

        Map<String, Object> drawingData = toDrawingData(drawing);
        eventPublisher.publishEvent(new DrawingChangedEvent(
                roomId,
                "drawing-created",
                drawing.getId(),
                drawingData,
                drawing.getUpdatedAt().toEpochMilli()
        ));

        return toResponse(drawing);
    }

    @Transactional
    public DrawingResponse moveDrawing(UUID roomId, UUID drawingId, Long userId, DrawingMoveRequest request) {
        Room room = roomService.memberRoom(roomId, userId);
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Room is closed");
        }

        Drawing drawing = drawingRepository.findByIdAndRoomAndDeletedFalse(drawingId, room)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing not found"));

        drawing.setOffsetX(request.offsetX() != null ? request.offsetX() : 0.0);
        drawing.setOffsetY(request.offsetY() != null ? request.offsetY() : 0.0);
        drawing.setUpdatedAt(Instant.now());

        drawingRepository.save(drawing);

        Map<String, Object> drawingData = toDrawingData(drawing);
        eventPublisher.publishEvent(new DrawingChangedEvent(
                roomId,
                "drawing-updated",
                drawing.getId(),
                drawingData,
                drawing.getUpdatedAt().toEpochMilli()
        ));

        return toResponse(drawing);
    }

    @Transactional
    public void deleteDrawing(UUID roomId, UUID drawingId, Long userId) {
        Room room = roomService.memberRoom(roomId, userId);
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Room is closed");
        }

        Drawing drawing = drawingRepository.findByIdAndRoomAndDeletedFalse(drawingId, room)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing not found"));

        boolean isAuthor = drawing.getAuthor() != null && drawing.getAuthor().getId().equals(userId);
        boolean isRoomOwner = room.getOwner() != null && room.getOwner().getId().equals(userId);

        if (!isAuthor && !isRoomOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or the room owner can delete this drawing");
        }

        drawing.setDeleted(true);
        drawing.setUpdatedAt(Instant.now());
        drawingRepository.save(drawing);

        eventPublisher.publishEvent(new DrawingChangedEvent(
                roomId,
                "drawing-deleted",
                drawing.getId(),
                null,
                drawing.getUpdatedAt().toEpochMilli()
        ));
    }

    public DrawingResponse toResponse(Drawing drawing) {
        UserResponse authorResponse = null;
        if (drawing.getAuthor() != null) {
            authorResponse = new UserResponse(
                    drawing.getAuthor().getId(),
                    drawing.getAuthor().getUsername(),
                    drawing.getAuthor().getEmail()
            );
        }

        return new DrawingResponse(
                drawing.getId(),
                drawing.getRoom().getId(),
                authorResponse,
                drawing.getKind(),
                drawing.getColor(),
                drawing.getWidth(),
                drawing.getOpacity(),
                drawing.getPayload(),
                drawing.getOffsetX(),
                drawing.getOffsetY(),
                drawing.getCreatedAt(),
                drawing.getUpdatedAt()
        );
    }

    public Map<String, Object> toDrawingData(Drawing drawing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", drawing.getId().toString());
        map.put("roomId", drawing.getRoom().getId().toString());
        if (drawing.getAuthor() != null) {
            Map<String, Object> authorMap = new HashMap<>();
            authorMap.put("id", drawing.getAuthor().getId());
            authorMap.put("username", drawing.getAuthor().getUsername() != null ? drawing.getAuthor().getUsername() : "");
            authorMap.put("email", drawing.getAuthor().getEmail() != null ? drawing.getAuthor().getEmail() : "");
            map.put("author", authorMap);
        }
        map.put("kind", drawing.getKind());
        map.put("color", drawing.getColor());
        map.put("width", drawing.getWidth() != null ? drawing.getWidth() : 5.0);
        map.put("opacity", drawing.getOpacity() != null ? drawing.getOpacity() : 1.0);
        map.put("payload", drawing.getPayload());
        map.put("offsetX", drawing.getOffsetX() != null ? drawing.getOffsetX() : 0.0);
        map.put("offsetY", drawing.getOffsetY() != null ? drawing.getOffsetY() : 0.0);
        map.put("createdAt", drawing.getCreatedAt() != null ? drawing.getCreatedAt().toString() : "");
        map.put("updatedAt", drawing.getUpdatedAt() != null ? drawing.getUpdatedAt().toString() : "");
        return map;
    }
}
