package com.sme.maintenance.comment;

import java.util.List;
import java.util.UUID;

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

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final IdeaFinderService ideaFinderService;
    private final RoomService roomService;
    private final UserRepository userRepository;

    public CommentService(
            CommentRepository commentRepository,
            IdeaFinderService ideaFinderService,
            RoomService roomService,
            UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.ideaFinderService = ideaFinderService;
        this.roomService = roomService;
        this.userRepository = userRepository;
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
        return commentRepository.save(comment);
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
        return commentRepository.save(reply);
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
        return commentRepository.save(comment);
    }

    public void deleteComment(UUID commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        roomService.requireOpen(comment.getIdea().getRoom());

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author may delete this comment");
        }

        commentRepository.delete(comment);
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
