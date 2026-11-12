package com.sme.maintenance.comment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.room.Room;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Override
    @EntityGraph(attributePaths = {"author", "idea.room.owner", "replies.author"})
    Optional<Comment> findById(UUID id);

    @EntityGraph(attributePaths = {"author", "replies.author"})
    List<Comment> findByIdeaAndParentCommentIsNullOrderByCreatedAtAsc(Idea idea);

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findByParentCommentOrderByCreatedAtAsc(Comment parentComment);

    long countByIdea(Idea idea);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.idea = :idea")
    void deleteByIdea(@Param("idea") Idea idea);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.idea.room = :room")
    void deleteByRoom(@Param("room") Room room);
}
