
package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.CategoryResponse;
import com.sme.maintenance.api.ApiModels.IdeaFilters;
import com.sme.maintenance.api.ApiModels.IdeaRequest;
import com.sme.maintenance.api.ApiModels.IdeaResponse;
import com.sme.maintenance.api.ApiModels.RoomResponse;
import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.repositories.CategoryRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;
import com.sme.maintenance.services.CategoryService;
import com.sme.maintenance.vote.VoteService;

@RestController
@RequestMapping("/api")
public class IdeaController {

    private final CategoryService categoryService;
    private final IdeaService ideaService;
    private final VoteService voteService;
    private final RoomService roomService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CategoryRepository categoryRepository;
    private final PresenceService presenceService;

    public IdeaController(
            IdeaService ideaService,
            VoteService voteService,
            RoomService roomService,
            AuthenticatedUserService authenticatedUserService,
            CategoryRepository categoryRepository,
            CategoryService categoryService,
            PresenceService presenceService
        ) {
        this.ideaService = ideaService;
        this.voteService = voteService;
        this.roomService = roomService;
        this.authenticatedUserService = authenticatedUserService;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.presenceService = presenceService;
    }

    @GetMapping("/rooms/{roomId}/ideas")
    public List<IdeaResponse> list(
            @PathVariable UUID roomId,
            @ModelAttribute IdeaFilters filters,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        return ideaService.list(roomId, userId,filters)
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

        Category category = null; // we first intialize it as null, if the category service is not null then we search for it and find it
        
        if(request.categoryId()!=null){
        category = categoryService.getCategoryById(request.categoryId()); // we get the category from the id
        
        }

        Idea idea = ideaService.create(
                roomId,
                request.text(),
                request.title(),
                request.description(),
                request.x(),
                request.y(),
                request.color(),
                userId,
                category
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
        
        Category category  = null; //we first assume the category is null, if the category request id is not null then
        // we search for the category

        if (request.categoryId()!=null){
        category = categoryService.getCategoryById(request.categoryId()); // we get the category from the id
        }

        Idea idea = ideaService.update(
                ideaId,
                request.text(),
                request.title(),
                request.description(),
                request.x(),
                request.y(),
                request.color(),
                userId,
                category
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

        boolean offline = presenceService.isRoomOffline(room.getId(), room.getOwnerId());

        RoomResponse roomResponse = new RoomResponse(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                new UserResponse(
                        room.getOwner().getId(),
                        room.getOwner().getUsername(),
                        null
                ),
                room.getCreatedAt(),
                members,
                members.size(),
                members.stream()
                        .anyMatch(member -> member.id().equals(userId)),
                room.isClosed(),
                offline
        );

        User author = idea.getAuthor();

        UserResponse authorResponse = new UserResponse(
                author.getId(),
                author.getUsername(),
                author.getEmail()
        );
        
        //preparing the category response as a part of the idea response
        Category category = idea.getCategory();
        CategoryResponse categoryResponse = null;

        if (category!=null){
        categoryResponse = new CategoryResponse(category.getId(),category.getTitle());
        }

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
                voteService.voted(idea.getId(), userId),
                categoryResponse
        );
    }
}
