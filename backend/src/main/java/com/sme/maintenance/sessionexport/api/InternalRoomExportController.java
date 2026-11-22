package com.sme.maintenance.sessionexport.api;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.sessionexport.dto.RoomExportDataResponse;
import com.sme.maintenance.sessionexport.service.RoomExportService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/rooms")
public class InternalRoomExportController {

    private final RoomRepository roomRepository;
    private final RoomExportService roomExportService;
    private final String expectedSecret;

    public InternalRoomExportController(
            RoomRepository roomRepository,
            RoomExportService roomExportService,
            @Value("${internal.service.secret:mindspark-internal-dev-key}") String expectedSecret) {
        this.roomRepository = roomRepository;
        this.roomExportService = roomExportService;
        this.expectedSecret = expectedSecret;
    }

    @GetMapping("/{roomId}/export-data")
    public ResponseEntity<RoomExportDataResponse> getRoomExportData(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey) {

        if (serviceKey == null || !serviceKey.equals(expectedSecret)) {
            log.warn("Unauthorized internal room export access attempt for roomId: {}", roomId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(roomExportService.exportRoomData(room));
    }
}
