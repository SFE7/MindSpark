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

import com.sme.maintenance.api.ApiModels.CommentRequest;
import com.sme.maintenance.api.ApiModels.CommentResponse;
import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.comment.Comment;
import com.sme.maintenance.comment.CommentService;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.services.AuthenticatedUserService;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;
    private final AuthenticatedUserService authenticatedUserService;

    public CommentController(
            CommentService commentService,
            AuthenticatedUserService authenticatedUserService) {
        this.commentService = commentService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/ideas/{ideaId}/comments")
    public List<CommentResponse> list(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        return commentService.listComments(ideaId, user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/ideas/{ideaId}/comments")
    public CommentResponse create(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CommentRequest request) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        String content = request != null ? request.effectiveContent() : "";
        Comment comment;
        if (request != null && request.parentId() != null) {
            comment = commentService.replyToComment(request.parentId(), user.getId(), content);
        } else {
            comment = commentService.addComment(ideaId, user.getId(), content);
        }
        return toResponse(comment);
    }

    @PostMapping("/comments/{commentId}/replies")
    public CommentResponse reply(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CommentRequest request) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        String content = request != null ? request.effectiveContent() : "";
        Comment reply = commentService.replyToComment(commentId, user.getId(), content);
        return toResponse(reply);
    }

    @PutMapping("/comments/{commentId}")
    public CommentResponse update(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CommentRequest request) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        String content = request != null ? request.effectiveContent() : "";
        Comment comment = commentService.updateComment(commentId, user.getId(), content);
        return toResponse(comment);
    }

    @DeleteMapping("/comments/{commentId}")
    public void delete(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        commentService.deleteComment(commentId, user.getId());
    }

    private CommentResponse toResponse(Comment comment) {
        User author = comment.getAuthor();
        UserResponse authorResponse = new UserResponse(
                author.getId(),
                author.getUsername(),
                author.getEmail()
        );

        List<CommentResponse> replies = comment.getReplies() != null
                ? comment.getReplies().stream()
                        .map(this::toResponse)
                        .toList()
                : List.of();

        UUID parentId = comment.getParentComment() != null ? comment.getParentComment().getId() : null;

        return new CommentResponse(
                comment.getId(),
                comment.getIdea().getId(),
                authorResponse,
                comment.getContent(),
                comment.getContent(),
                parentId,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies
        );
    }
}
