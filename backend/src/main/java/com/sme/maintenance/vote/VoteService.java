
package com.sme.maintenance.vote;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaFinderService;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.websocket.IdeaChangedEvent;

@Service
@Transactional
public class VoteService {

    private final VoteRepository voteRepository;
    private final IdeaRepository ideaRepository;
    private final IdeaFinderService ideaFinderService;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VoteService(
            VoteRepository voteRepository,
            IdeaRepository ideaRepository,
            IdeaFinderService ideaFinderService,
            RoomService roomService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.voteRepository = voteRepository;
        this.ideaRepository = ideaRepository;
        this.ideaFinderService = ideaFinderService;
        this.roomService = roomService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }
//As a client, I can vote for an idea.
    public void add(UUID ideaId, Long userId) {
        Idea idea = ideaFinderService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(idea.getRoom());

        User user = getUser(userId);

        if (!voteRepository.existsByIdeaAndUser(idea, user)) {
            voteRepository.save(new Vote(idea, user));
            idea.updatePosition(idea.getX(), idea.getY());
            Idea saved = ideaRepository.save(idea);
            eventPublisher.publishEvent(new IdeaChangedEvent(
                    saved.getRoom().getId(),
                    "idea-updated",
                    saved.getId(),
                    toIdeaData(saved),
                    saved.getUpdatedAt().toEpochMilli()
            ));
        }
    }

    public void remove(UUID ideaId, Long userId) {
        Idea idea = ideaFinderService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(idea.getRoom());

        User user = getUser(userId);

        voteRepository.findByIdeaAndUser(idea, user)
                .ifPresent(v -> {
                    voteRepository.delete(v);
                    idea.updatePosition(idea.getX(), idea.getY());
                    Idea saved = ideaRepository.save(idea);
                    eventPublisher.publishEvent(new IdeaChangedEvent(
                            saved.getRoom().getId(),
                            "idea-updated",
                            saved.getId(),
                            toIdeaData(saved),
                            saved.getUpdatedAt().toEpochMilli()
                    ));
                });
    }

    private Map<String, Object> toIdeaData(Idea idea) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", idea.getId().toString());
        map.put("text", idea.getText());
        map.put("title", idea.getTitle());
        map.put("description", idea.getDescription());
        map.put("x", idea.getX());
        map.put("y", idea.getY());
        map.put("color", idea.getColor());
        map.put("createdAt", idea.getCreatedAt().toString());
        map.put("updatedAt", idea.getUpdatedAt().toString());
        map.put("voteCount", voteRepository.countByIdea(idea));

        Map<String, Object> roomMap = new java.util.LinkedHashMap<>();
        roomMap.put("id", idea.getRoom().getId().toString());
        roomMap.put("name", idea.getRoom().getName());
        map.put("room", roomMap);

        Map<String, Object> authorMap = new java.util.LinkedHashMap<>();
        authorMap.put("id", idea.getAuthor().getId());
        authorMap.put("username", idea.getAuthor().getUsername());
        authorMap.put("email", idea.getAuthor().getEmail());
        map.put("author", authorMap);

        if (idea.getCategory() != null) {
            Map<String, Object> catMap = new java.util.LinkedHashMap<>();
            catMap.put("id", idea.getCategory().getId());
            catMap.put("title", idea.getCategory().getTitle());
            map.put("category", catMap);
        } else {
            map.put("category", null);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public long count(UUID ideaId) {
        Idea idea = ideaFinderService.find(ideaId);
        return voteRepository.countByIdea(idea);
    }

    @Transactional(readOnly = true)
    public boolean voted(UUID ideaId, Long userId) {
        Idea idea = ideaFinderService.find(ideaId);
        User user = getUser(userId);

        return voteRepository.existsByIdeaAndUser(idea, user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }
}
