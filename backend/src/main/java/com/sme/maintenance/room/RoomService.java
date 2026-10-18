package com.sme.maintenance.room;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public Room create(String name, UUID guestId) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name is required");
        }
        String code;
        do {
            code = generateCode();
        } while (roomRepository.findByInvitationCode(code).isPresent());
        return roomRepository.save(new Room(name.trim(), code, guestId));
    }

    public List<Room> ownedBy(UUID guestId) {
        return roomRepository.findByOwnerGuestIdOrderByCreatedAtDesc(guestId);
    }

    public Room join(String invitationCode, UUID guestId) {
        if (invitationCode == null || invitationCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation code is required");
        }
        Room room = roomRepository.findByInvitationCode(invitationCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        room.getMemberGuestIds().add(guestId);
        return roomRepository.save(room);
    }

    public Room reopen(UUID roomId, UUID guestId) {
        Room room = find(roomId);
        requireOwner(room, guestId);
        room.getMemberGuestIds().add(guestId);
        return roomRepository.save(room);
    }

    public Room leave(UUID roomId, UUID guestId) {
        Room room = find(roomId);
        requireMember(room, guestId);
        room.getMemberGuestIds().remove(guestId);
        return roomRepository.save(room);
    }

    public Room memberRoom(UUID roomId, UUID guestId) {
        Room room = find(roomId);
        requireMember(room, guestId);
        return room;
    }

    public Room find(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    public void requireMember(Room room, UUID guestId) {
        if (!room.getMemberGuestIds().contains(guestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room first");
        }
    }

    private void requireOwner(Room room, UUID guestId) {
        if (!room.getOwnerGuestId().equals(guestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room owner can reopen it");
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}