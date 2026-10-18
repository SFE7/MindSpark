package com.sme.maintenance.vote;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.room.RoomService;

@Service
public class VoteService {
    private final VoteRepository voteRepository;
    private final IdeaService ideaService;
    private final RoomService roomService;

    public VoteService(VoteRepository voteRepository, IdeaService ideaService, RoomService roomService) {
        this.voteRepository = voteRepository;
        this.ideaService = ideaService;
        this.roomService = roomService;
    }

    public void add(UUID ideaId, UUID guestId) {
        Idea idea = ideaService.find(ideaId);
        roomService.memberRoom(idea.getRoomId(), guestId);
        if (voteRepository.findByIdeaIdAndGuestId(ideaId, guestId).isEmpty()) {
            voteRepository.save(new Vote(ideaId, guestId));
        }
    }

    public void remove(UUID ideaId, UUID guestId) {
        Idea idea = ideaService.find(ideaId);
        roomService.memberRoom(idea.getRoomId(), guestId);
        voteRepository.findByIdeaIdAndGuestId(ideaId, guestId).ifPresent(voteRepository::delete);
    }

    public long count(UUID ideaId) {
        return voteRepository.findByIdeaId(ideaId).size();
    }

    public boolean voted(UUID ideaId, UUID guestId) {
        return voteRepository.findByIdeaIdAndGuestId(ideaId, guestId).isPresent();
    }
}