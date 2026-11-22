package com.sme.maintenance.votepool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotePoolRepository extends JpaRepository<VotePool, UUID> {

    @EntityGraph(attributePaths = {"room", "createdBy"})
    @Query("SELECT vp FROM VotePool vp WHERE vp.room.id = :roomId AND vp.status = 'OPEN'")
    Optional<VotePool> findActivePoolByRoomId(@Param("roomId") UUID roomId);

    @EntityGraph(attributePaths = {"room", "createdBy"})
    @Query("SELECT vp FROM VotePool vp WHERE vp.room.id = :roomId AND vp.status = 'CLOSED' ORDER BY vp.closedAt DESC")
    List<VotePool> findLatestClosedPools(@Param("roomId") UUID roomId);

    @EntityGraph(attributePaths = {"room", "createdBy"})
    @Query("SELECT vp FROM VotePool vp WHERE vp.room.id = :roomId ORDER BY vp.createdAt DESC")
    List<VotePool> findByRoomIdOrderByCreatedAtDesc(@Param("roomId") UUID roomId);
}
