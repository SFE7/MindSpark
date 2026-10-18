package com.sme.maintenance.idea;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    List<Idea> findByRoomIdOrderByCreatedAtAsc(UUID roomId);
}