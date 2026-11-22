package com.sme.maintenance.media;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.media.dto.RoomAccessResponse;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/rooms")
public class InternalRoomAccessController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final String expectedSecret;

    public InternalRoomAccessController(
            RoomRepository roomRepository,
            UserRepository userRepository,
            @Value("${internal.service.secret:mindspark-internal-dev-key}") String expectedSecret) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.expectedSecret = expectedSecret;
    }

    @GetMapping("/{roomId}/access/{keycloakSub}")
    public ResponseEntity<RoomAccessResponse> checkRoomAccess(
            @PathVariable UUID roomId,
            @PathVariable String keycloakSub,
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey) {

        if (serviceKey == null || !serviceKey.equals(expectedSecret)) {
            log.warn("Unauthorized internal room access verification attempt for roomId: {}", roomId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String sub = keycloakSub != null ? keycloakSub.trim() : "";
        User user = userRepository.findByKeycloakId(sub).orElse(null);

        boolean isOwner = false;
        if (room.getOwner() != null) {
            if (user != null && room.getOwner().getId().equals(user.getId())) {
                isOwner = true;
            } else if (room.getOwner().getKeycloakId() != null && room.getOwner().getKeycloakId().equals(sub)) {
                isOwner = true;
            }
        }

        boolean isMember = isOwner;
        if (!isMember && user != null && room.getMemberIds() != null) {
            isMember = room.getMemberIds().contains(user.getId());
        }

        return ResponseEntity.ok(new RoomAccessResponse(room.getId(), isMember, isOwner, room.isClosed()));
    }
}
