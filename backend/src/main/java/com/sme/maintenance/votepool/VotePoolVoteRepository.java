package com.sme.maintenance.votepool;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotePoolVoteRepository extends JpaRepository<VotePoolVote, UUID> {

    long countByPoolIdAndUserId(UUID poolId, Long userId);

    @Query("SELECT COUNT(v) FROM VotePoolVote v WHERE v.pool.id = :poolId AND v.user.id = :userId")
    long countVotesForUserInPool(@Param("poolId") UUID poolId, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"idea", "user"})
    List<VotePoolVote> findByPoolIdAndUserId(UUID poolId, Long userId);

    @EntityGraph(attributePaths = {"idea", "user"})
    List<VotePoolVote> findByPoolIdAndUserIdAndIdeaId(UUID poolId, Long userId, UUID ideaId);

    @EntityGraph(attributePaths = {"idea", "user"})
    List<VotePoolVote> findByPoolId(UUID poolId);

    @Query("SELECT v.idea.id, COUNT(v) FROM VotePoolVote v WHERE v.pool.id = :poolId GROUP BY v.idea.id")
    List<Object[]> countVotesGroupedByIdeaId(@Param("poolId") UUID poolId);
}
