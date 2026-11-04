package com.sme.maintenance.idea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.room.Room;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    @Override
    @EntityGraph(attributePaths = {"room.owner", "author"})
    Optional<Idea> findById(UUID id);

    @EntityGraph(attributePaths = {"room.owner", "author"})
    List<Idea> findByRoomOrderByCreatedAtAsc(Room room);
}