
package com.sme.maintenance.vote;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaFinderService;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.websocket.RoomBroadcastService;
import com.sme.maintenance.websocket.RoomEvent;

@Service
@Transactional
public class VoteService {

    private final VoteRepository voteRepository;
    private final IdeaFinderService ideaFinderService;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final RoomBroadcastService broadcastService;

    @Autowired
    public VoteService(
            VoteRepository voteRepository,
            IdeaFinderService ideaFinderService,
            RoomService roomService,
            UserRepository userRepository,
            @Autowired(required = false) RoomBroadcastService broadcastService) {
        this.voteRepository = voteRepository;
        this.ideaFinderService = ideaFinderService;
        this.roomService = roomService;
        this.userRepository = userRepository;
        this.broadcastService = broadcastService;
    }

    public VoteService(
            VoteRepository voteRepository,
            IdeaFinderService ideaFinderService,
            RoomService roomService,
            UserRepository userRepository) {
        this(voteRepository, ideaFinderService, roomService, userRepository, null);
    }

    private void broadcastVoteChanged(Idea idea) {
        if (broadcastService == null) return;
        UUID roomId = idea.getRoom().getId();
        broadcastService.broadcastAfterCommit(roomId, () -> {
            long seq = broadcastService.nextSequence(roomId);
            Instant now = broadcastService.getClock() != null ? broadcastService.getClock().instant() : Instant.now();
            long count = voteRepository.countByIdea(idea);
            Long catId = idea.getCategory() != null ? idea.getCategory().getId() : null;
            String catTitle = idea.getCategory() != null ? idea.getCategory().getTitle() : null;
            RoomEvent.SharedIdea shared = new RoomEvent.SharedIdea(
                    idea.getId(),
                    idea.getTitle(),
                    idea.getDescription(),
                    idea.getText(),
                    idea.getX(),
                    idea.getY(),
                    idea.getColor(),
                    catId,
                    catTitle,
                    count,
                    idea.getCreatedAt(),
                    idea.getUpdatedAt()
            );
            return RoomEvent.ideaVoted(roomId, seq, now, shared);
        });
    }

//As a client, I can vote for an idea.
    public void add(UUID ideaId, Long userId) {
        Idea idea = ideaFinderService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(idea.getRoom());

        User user = getUser(userId);

        if (!voteRepository.existsByIdeaAndUser(idea, user)) {
            voteRepository.save(new Vote(idea, user));
            broadcastVoteChanged(idea);
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
                    broadcastVoteChanged(idea);
                });
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
