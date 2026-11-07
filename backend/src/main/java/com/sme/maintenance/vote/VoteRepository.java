package com.sme.maintenance.vote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByIdea(Idea idea);

    Optional<Vote> findByIdeaAndUser(Idea idea, User user);

    boolean existsByIdeaAndUser(Idea idea, User user);

    void deleteByIdeaAndUser(Idea idea, User user);

    long countByIdea(Idea idea);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Vote v WHERE v.idea.room = :room")
    void deleteByRoom(@org.springframework.data.repository.query.Param("room") com.sme.maintenance.room.Room room);
}