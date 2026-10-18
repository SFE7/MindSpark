package com.sme.maintenance.vote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByIdeaId(UUID ideaId);
    Optional<Vote> findByIdeaIdAndGuestId(UUID ideaId, UUID guestId);
}