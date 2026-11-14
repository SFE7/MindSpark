package com.sme.maintenance.room;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.repositories.UserRepository;
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

    public RoomService(
            RoomRepository roomRepository,
            UserRepository userRepository,
            IdeaRepository ideaRepository,
            VoteRepository voteRepository,
            PresenceService presenceService) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.ideaRepository = ideaRepository;
        this.voteRepository = voteRepository;
        this.presenceService = presenceService;
    }

    // i can create a room
    public Room create(String name, Long userId) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name is required");
        }
        String code;
        do {
            code = generateCode();
        } while (roomRepository.findByInvitationCode(code).isPresent());
        User user = getUser(userId);
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

    public Room join(String invitationCode, Long userId) {
        if (invitationCode == null || invitationCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation code is required");
        }
        Room room = roomRepository.findByInvitationCode(invitationCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (room.isClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join a closed room");
        }
        getUser(userId);
        room.getMemberIds().add(userId);
        return roomRepository.save(room);
    }

    public Room close(UUID roomId, Long userId) {
        Room room = find(roomId);
        requireOwner(room, userId);
        room.setClosed(true);
        return roomRepository.save(room);
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
}