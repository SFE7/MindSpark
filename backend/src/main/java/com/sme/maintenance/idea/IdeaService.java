package com.sme.maintenance.idea;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;

@Service
public class IdeaService {
    private final IdeaRepository ideaRepository;
    private final RoomService roomService;

    public IdeaService(IdeaRepository ideaRepository, RoomService roomService) {
        this.ideaRepository = ideaRepository;
        this.roomService = roomService;
    }

    public List<Idea> list(UUID roomId, UUID guestId) {
        roomService.memberRoom(roomId, guestId);
        return ideaRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }

    public Idea create(UUID roomId, String text, UUID guestId) {
        roomService.memberRoom(roomId, guestId);
        validateText(text);
        return ideaRepository.save(new Idea(roomId, guestId, text.trim()));
    }

    public Idea update(UUID ideaId, String text, UUID guestId) {
        Idea idea = find(ideaId);
        roomService.memberRoom(idea.getRoomId(), guestId);
        requireAuthor(idea, guestId);
        validateText(text);
        idea.updateText(text.trim());
        return ideaRepository.save(idea);
    }

    public void delete(UUID ideaId, UUID guestId) {
        Idea idea = find(ideaId);
        roomService.memberRoom(idea.getRoomId(), guestId);
        requireAuthor(idea, guestId);
        ideaRepository.delete(idea);
    }

    public Idea find(UUID ideaId) {
        return ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Idea not found"));
    }

    private void requireAuthor(Idea idea, UUID guestId) {
        if (!idea.getAuthorGuestId().equals(guestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can change this idea");
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea text is required");
        }
    }
}