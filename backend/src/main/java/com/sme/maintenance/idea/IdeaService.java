
package com.sme.maintenance.idea;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;

@Service
@Transactional
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public IdeaService(
            IdeaRepository ideaRepository,
            RoomService roomService,
            RoomRepository roomRepository,
            UserRepository userRepository) {
        this.ideaRepository = ideaRepository;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Idea> list(UUID roomId, Long userId) {
        roomService.memberRoom(roomId, userId);

        Room room = getRoom(roomId);

        return ideaRepository.findByRoomOrderByCreatedAtAsc(room);
    }
// I can create an idea in a room I belong to
    public Idea create(UUID roomId, String text, Long userId) {
        return create(
                roomId, text, text, null,
                null, null, null, userId
        );
    }

    public Idea create(
            UUID roomId,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Long userId) {

        Room room = roomService.memberRoom(roomId, userId);
        User author = getUser(userId);

        String effectiveTitle =
                title != null && !title.isBlank()
                        ? title.trim()
                        : (text != null ? text.trim() : "");

        if (effectiveTitle.isEmpty() || effectiveTitle.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea title or text is required"
            );
        }

        if (text != null && text.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea text is too long"
            );
        }

        if (description != null && description.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea description is too long"
            );
        }

        Idea idea = new Idea(
                room,
                author,
                text,
                effectiveTitle,
                description,
                x,
                y,
                color
        );

        return ideaRepository.save(idea);
    }

    public Idea update(UUID ideaId, String text, Long userId) {
        return update(
                ideaId, text, text, null,
                null, null, null, userId
        );
    }

    public Idea update(
            UUID ideaId,
            String text,
            String title,
            String description,
            Long userId) {

        return update(
                ideaId, text, title, description,
                null, null, null, userId
        );
    }

    public Idea update(
            UUID ideaId,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Long userId) {

        Idea idea = find(ideaId);

        roomService.memberRoom(idea.getRoom().getId(), userId);

        String currentTitle = idea.getTitle() != null
                ? idea.getTitle()
                : (idea.getText() != null ? idea.getText() : "");

        String currentDescription = idea.getDescription() != null
                ? idea.getDescription()
                : "";

        String currentColor = idea.getColor() != null
                ? idea.getColor()
                : "";

        boolean titleChanged =
                title != null
                && !title.isBlank()
                && !title.trim().equals(currentTitle.trim());

        boolean textChanged =
                text != null
                && !text.isBlank()
                && !text.trim().equals(
                        idea.getText() != null
                                ? idea.getText().trim()
                                : ""
                );

        boolean descriptionChanged =
                description != null
                && !description.trim().equals(currentDescription.trim());

        boolean colorChanged =
                color != null
                && !color.isBlank()
                && !color.equalsIgnoreCase(currentColor);

        if (titleChanged || textChanged || descriptionChanged || colorChanged) {
            requireAuthor(idea, userId);
        }

        String effectiveTitle =
                title != null && !title.isBlank()
                        ? title.trim()
                        : (text != null && !text.isBlank()
                                ? text.trim()
                                : currentTitle);

        String effectiveText =
                text != null && !text.isBlank()
                        ? text.trim()
                        : (idea.getText() != null
                                ? idea.getText()
                                : effectiveTitle);

        String effectiveDescription =
                description != null
                        ? description.trim()
                        : currentDescription;

        if (effectiveTitle.isBlank() || effectiveTitle.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea title is required and must not exceed 200 characters"
            );
        }

        if (effectiveText.isBlank() || effectiveText.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea text is required and must not exceed 2000 characters"
            );
        }

        if (effectiveDescription.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea description is too long"
            );
        }

        Double newX = x != null ? x : idea.getX();
        Double newY = y != null ? y : idea.getY();

        String newColor =
                color != null && !color.isBlank()
                        ? color
                        : idea.getColor();

        idea.update(
                effectiveText,
                effectiveTitle,
                effectiveDescription,
                newX,
                newY,
                newColor
        );

        return ideaRepository.save(idea);
    }
// I can delete my own ideas
    public void delete(UUID ideaId, Long userId) {
        Idea idea = find(ideaId);

        roomService.memberRoom(idea.getRoom().getId(), userId);

        requireAuthor(idea, userId);

        ideaRepository.delete(idea);
    }

    @Transactional(readOnly = true)
    public Idea find(UUID ideaId) {
        return ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Idea not found"
                ));
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required"
            );
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private void requireAuthor(Idea idea, Long userId) {
        if (userId == null
                || idea.getAuthor() == null
                || !idea.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the author can change this idea"
            );
        }
    }
}
