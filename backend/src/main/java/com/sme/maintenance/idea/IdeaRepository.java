package com.sme.maintenance.idea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.entities.Category;
import com.sme.maintenance.room.Room;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    @Override
    @EntityGraph(attributePaths = {"room.owner", "author"})
    Optional<Idea> findById(UUID id);

    @EntityGraph(attributePaths = {"room.owner", "author"})
    List<Idea> findByRoomOrderByCreatedAtAsc(Room room);

    @EntityGraph(attributePaths = {"room.owner", "author"})
    List<Idea> findByRoom(Room room);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Idea i WHERE i.room = :room")
    void deleteByRoom(@org.springframework.data.repository.query.Param("room") Room room);
}