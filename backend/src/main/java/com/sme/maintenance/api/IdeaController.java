
package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.IdeaRequest;
import com.sme.maintenance.api.ApiModels.IdeaResponse;
import com.sme.maintenance.api.ApiModels.RoomResponse;
import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;
import com.sme.maintenance.vote.VoteService;

@RestController
@RequestMapping("/api")
public class IdeaController {

    private final IdeaService ideaService;
    private final VoteService voteService;
    private final RoomService roomService;
    private final AuthenticatedUserService authenticatedUserService;

    public IdeaController(
            IdeaService ideaService,
            VoteService voteService,
            RoomService roomService,
            AuthenticatedUserService authenticatedUserService) {
        this.ideaService = ideaService;
        this.voteService = voteService;
        this.roomService = roomService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/rooms/{roomId}/ideas")
    public List<IdeaResponse> list(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        return ideaService.list(roomId, userId)
                .stream()
                .map(idea -> response(idea, userId))
                .toList();
    }

    @PostMapping("/rooms/{roomId}/ideas")
    public IdeaResponse create(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody IdeaRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        Idea idea = ideaService.create(
                roomId,
                request.text(),
                request.title(),
                request.description(),
                request.x(),
                request.y(),
                request.color(),
                userId
        );

        return response(idea, userId);
    }

    @PutMapping("/ideas/{ideaId}")
    public IdeaResponse update(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody IdeaRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        Idea idea = ideaService.update(
                ideaId,
                request.text(),
                request.title(),
                request.description(),
                request.x(),
                request.y(),
                request.color(),
                userId
        );

        return response(idea, userId);
    }

    @DeleteMapping("/ideas/{ideaId}")
    public void delete(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        ideaService.delete(ideaId, user.getId());
    }

    @PostMapping("/ideas/{ideaId}/vote")
    public IdeaResponse vote(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        voteService.add(ideaId, userId);

        return response(ideaService.find(ideaId), userId);
    }

    @DeleteMapping("/ideas/{ideaId}/vote")
    public IdeaResponse removeVote(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        voteService.remove(ideaId, userId);

        return response(ideaService.find(ideaId), userId);
    }

    private IdeaResponse response(Idea idea, Long userId) {

        Room room = idea.getRoom();

        List<UserResponse> members = roomService.members(room.getId())
                .stream()
                .map(member -> new UserResponse(
                        member.getId(),
                        member.getUsername(),
                        member.getEmail()
                ))
                .toList();

        RoomResponse roomResponse = new RoomResponse(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                new UserResponse(
                        room.getOwner().getId(),
                        room.getOwner().getUsername(),
                        room.getOwner().getEmail()
                ),
                room.getCreatedAt(),
                members,
                members.size(),
                members.stream()
                        .anyMatch(member -> member.id().equals(userId))
        );

        User author = idea.getAuthor();

        UserResponse authorResponse = new UserResponse(
                author.getId(),
                author.getUsername(),
                author.getEmail()
        );

        return new IdeaResponse(
                idea.getId(),
                roomResponse,
                authorResponse,
                idea.getText(),
                idea.getTitle(),
                idea.getDescription(),
                idea.getX(),
                idea.getY(),
                idea.getColor(),
                idea.getCreatedAt(),
                idea.getUpdatedAt(),
                voteService.count(idea.getId()),
                voteService.voted(idea.getId(), userId)
        );
    }
}
