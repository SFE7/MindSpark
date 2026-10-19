
package com.sme.maintenance.vote;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.room.RoomService;

@Service
@Transactional
public class VoteService {

    private final VoteRepository voteRepository;
    private final IdeaService ideaService;
    private final RoomService roomService;
    private final UserRepository userRepository;

    public VoteService(
            VoteRepository voteRepository,
            IdeaService ideaService,
            RoomService roomService,
            UserRepository userRepository) {
        this.voteRepository = voteRepository;
        this.ideaService = ideaService;
        this.roomService = roomService;
        this.userRepository = userRepository;
    }

    public void add(UUID ideaId, Long userId) {
        Idea idea = ideaService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);

        User user = getUser(userId);

        if (!voteRepository.existsByIdeaAndUser(idea, user)) {
            voteRepository.save(new Vote(idea, user));
        }
    }

    public void remove(UUID ideaId, Long userId) {
        Idea idea = ideaService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);

        User user = getUser(userId);

        voteRepository.findByIdeaAndUser(idea, user)
                .ifPresent(voteRepository::delete);
    }

    @Transactional(readOnly = true)
    public long count(UUID ideaId) {
        Idea idea = ideaService.find(ideaId);
        return voteRepository.countByIdea(idea);
    }

    @Transactional(readOnly = true)
    public boolean voted(UUID ideaId, Long userId) {
        Idea idea = ideaService.find(ideaId);
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
