package com.sme.maintenance.media;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaAttachmentRepository extends JpaRepository<IdeaAttachment, UUID> {

    @EntityGraph(attributePaths = {"idea", "uploadedBy"})
    List<IdeaAttachment> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    @EntityGraph(attributePaths = {"idea", "uploadedBy"})
    List<IdeaAttachment> findByIdeaIdOrderByCreatedAtAsc(UUID ideaId);

    long countByIdeaId(UUID ideaId);
}
