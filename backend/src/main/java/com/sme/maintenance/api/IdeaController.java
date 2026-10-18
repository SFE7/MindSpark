package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.vote.VoteService;

import static com.sme.maintenance.api.ApiModels.*;

@RestController
@RequestMapping("/api")
public class IdeaController {
    private final IdeaService ideaService;
    private final VoteService voteService;

    public IdeaController(IdeaService ideaService, VoteService voteService) {
        this.ideaService = ideaService;
        this.voteService = voteService;
    }

    @GetMapping("/rooms/{roomId}/ideas")
    public List<IdeaResponse> list(@PathVariable UUID roomId, @RequestHeader("X-Guest-Id") UUID guestId) {
        return ideaService.list(roomId, guestId).stream().map(idea -> response(idea, guestId)).toList();
    }

    @PostMapping("/rooms/{roomId}/ideas")
    public IdeaResponse create(@PathVariable UUID roomId, @RequestHeader("X-Guest-Id") UUID guestId,
                               @RequestBody IdeaRequest request) {
        return response(ideaService.create(roomId, request.text(), guestId), guestId);
    }

    @PutMapping("/ideas/{ideaId}")
    public IdeaResponse update(@PathVariable UUID ideaId, @RequestHeader("X-Guest-Id") UUID guestId,
                               @RequestBody IdeaRequest request) {
        return response(ideaService.update(ideaId, request.text(), guestId), guestId);
    }

    @DeleteMapping("/ideas/{ideaId}")
    public void delete(@PathVariable UUID ideaId, @RequestHeader("X-Guest-Id") UUID guestId) {
        ideaService.delete(ideaId, guestId);
    }

    @PostMapping("/ideas/{ideaId}/vote")
    public IdeaResponse vote(@PathVariable UUID ideaId, @RequestHeader("X-Guest-Id") UUID guestId) {
        voteService.add(ideaId, guestId);
        return response(ideaService.find(ideaId), guestId);
    }

    @DeleteMapping("/ideas/{ideaId}/vote")
    public IdeaResponse removeVote(@PathVariable UUID ideaId, @RequestHeader("X-Guest-Id") UUID guestId) {
        voteService.remove(ideaId, guestId);
        return response(ideaService.find(ideaId), guestId);
    }

    private IdeaResponse response(Idea idea, UUID guestId) {
        return new IdeaResponse(idea.getId(), idea.getRoomId(), idea.getAuthorGuestId(), idea.getText(),
                idea.getCreatedAt(), idea.getUpdatedAt(), voteService.count(idea.getId()),
                voteService.voted(idea.getId(), guestId));
    }
}