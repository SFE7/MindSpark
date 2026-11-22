package com.sme.maintenance.media;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanvasImageRepository extends JpaRepository<CanvasImage, UUID> {

    @EntityGraph(attributePaths = {"room", "uploadedBy"})
    List<CanvasImage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    long countByRoomId(UUID roomId);
}
