package com.sme.maintenance.comment;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaFinderService;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.websocket.CommentChangedEvent;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final IdeaFinderService ideaFinderService;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(
            CommentRepository commentRepository,
            IdeaFinderService ideaFinderService,
            RoomService roomService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.ideaFinderService = ideaFinderService;
        this.roomService = roomService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public Comment addComment(UUID ideaId, Long userId, String content) {
        Idea idea = ideaFinderService.find(ideaId);
        Room room = roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(room);

        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        if (content.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is too long");
        }

        User author = getUser(userId);
        Comment comment = new Comment(idea, author, content.trim());
        Comment saved = commentRepository.save(comment);
        eventPublisher.publishEvent(new CommentChangedEvent(
                idea.getRoom().getId(),
                "comment-created",
                idea.getId(),
                saved.getId(),
                toCommentData(saved),
                saved.getUpdatedAt().toEpochMilli()
        ));
        return saved;
    }

    public Comment replyToComment(UUID parentCommentId, Long userId, String content) {
        Comment parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (parent.getParentComment() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replies can only target top-level comments");
        }

        Idea idea = parent.getIdea();
        Room room = roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(room);

        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply content is required");
        }
        if (content.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply content is too long");
        }

        User author = getUser(userId);
        Comment reply = new Comment(idea, author, content.trim(), parent);
        Comment saved = commentRepository.save(reply);
        eventPublisher.publishEvent(new CommentChangedEvent(
                idea.getRoom().getId(),
                "comment-created",
                idea.getId(),
                saved.getId(),
                toCommentData(saved),
                saved.getUpdatedAt().toEpochMilli()
        ));
        return saved;
    }

    public Comment updateComment(UUID commentId, Long userId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        roomService.requireOpen(comment.getIdea().getRoom());

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author may edit this comment");
        }

        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        if (content.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is too long");
        }

        comment.setContent(content.trim());
        Comment saved = commentRepository.save(comment);
        eventPublisher.publishEvent(new CommentChangedEvent(
                saved.getIdea().getRoom().getId(),
                "comment-updated",
                saved.getIdea().getId(),
                saved.getId(),
                toCommentData(saved),
                saved.getUpdatedAt().toEpochMilli()
        ));
        return saved;
    }

    public void deleteComment(UUID commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        roomService.requireOpen(comment.getIdea().getRoom());

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author may delete this comment");
        }

        UUID roomId = comment.getIdea().getRoom().getId();
        UUID ideaId = comment.getIdea().getId();
        long version = System.currentTimeMillis();
        commentRepository.delete(comment);
        eventPublisher.publishEvent(new CommentChangedEvent(
                roomId,
                "comment-deleted",
                ideaId,
                commentId,
                null,
                version
        ));
    }

    public Map<String, Object> toCommentData(Comment comment) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", comment.getId().toString());
        map.put("ideaId", comment.getIdea().getId().toString());
        map.put("content", comment.getContent());
        map.put("text", comment.getContent());
        map.put("parentId", comment.getParentComment() != null ? comment.getParentComment().getId().toString() : null);
        map.put("createdAt", comment.getCreatedAt().toString());
        map.put("updatedAt", comment.getUpdatedAt().toString());

        Map<String, Object> authorMap = new java.util.LinkedHashMap<>();
        authorMap.put("id", comment.getAuthor().getId());
        authorMap.put("username", comment.getAuthor().getUsername());
        authorMap.put("email", comment.getAuthor().getEmail());
        map.put("author", authorMap);

        return map;
    }

    @Transactional(readOnly = true)
    public List<Comment> listComments(UUID ideaId, Long userId) {
        Idea idea = ideaFinderService.find(ideaId);
        roomService.memberRoom(idea.getRoom().getId(), userId);
        return commentRepository.findByIdeaAndParentCommentIsNullOrderByCreatedAtAsc(idea);
    }

    @Transactional(readOnly = true)
    public Comment findById(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
