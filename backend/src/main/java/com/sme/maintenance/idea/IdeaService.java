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
        return create(roomId, text, text, null, null, null, null, guestId);
    }

    public Idea create(UUID roomId, String text, String title, String description, Double x, Double y, String color, UUID guestId) {
        roomService.memberRoom(roomId, guestId);
        String effectiveTitle = title != null && !title.isBlank() ? title.trim() : (text != null ? text.trim() : "");
        if (effectiveTitle.isEmpty() || effectiveTitle.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea title or text is required");
        }
        if (description != null && description.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea description is too long");
        }
        return ideaRepository.save(new Idea(roomId, guestId, text, effectiveTitle, description, x, y, color));
    }

    public Idea update(UUID ideaId, String text, UUID guestId) {
        return update(ideaId, text, text, null, null, null, null, guestId);
    }

    public Idea update(UUID ideaId, String text, String title, String description, UUID guestId) {
        return update(ideaId, text, title, description, null, null, null, guestId);
    }

    public Idea update(UUID ideaId, String text, String title, String description, Double x, Double y, String color, UUID guestId) {
        Idea idea = find(ideaId);
        roomService.memberRoom(idea.getRoomId(), guestId);

        String currentTitle = idea.getTitle() != null ? idea.getTitle() : (idea.getText() != null ? idea.getText() : "");
        String currentDesc = idea.getDescription() != null ? idea.getDescription() : "";
        String currentColor = idea.getColor() != null ? idea.getColor() : "";

        boolean titleChanged = title != null && !title.isBlank() && !title.trim().equals(currentTitle.trim());
        boolean textChanged = text != null && !text.isBlank() && !text.trim().equals(idea.getText() != null ? idea.getText().trim() : "");
        boolean descChanged = description != null && !description.trim().equals(currentDesc.trim());
        boolean colorChanged = color != null && !color.isBlank() && !color.equalsIgnoreCase(currentColor);

        if (titleChanged || textChanged || descChanged || colorChanged) {
            requireAuthor(idea, guestId);
        }

        String existingTitle = idea.getTitle() != null ? idea.getTitle() : (idea.getText() != null ? idea.getText() : "");
        String effectiveTitle = title != null && !title.isBlank() ? title.trim() : (text != null && !text.isBlank() ? text.trim() : existingTitle);
        String effectiveDescription = description != null ? description.trim() : (idea.getDescription() != null ? idea.getDescription() : "");
        String effectiveText = text != null && !text.isBlank() ? text.trim() : (idea.getText() != null ? idea.getText() : effectiveTitle);

        if (effectiveTitle != null && effectiveTitle.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea title is too long");
        }
        if (effectiveDescription != null && effectiveDescription.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea description is too long");
        }

        Double newX = x != null ? x : idea.getX();
        Double newY = y != null ? y : idea.getY();
        String newColor = color != null && !color.isBlank() ? color : idea.getColor();

        idea.update(effectiveText, effectiveTitle, effectiveDescription, newX, newY, newColor);
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