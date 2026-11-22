package com.sme.maintenance.votepool;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "vote_pool_votes",
        indexes = {
                @Index(name = "idx_pool_user", columnList = "pool_id, user_id"),
                @Index(name = "idx_pool_idea", columnList = "pool_id, idea_id")
        }
)
public class VotePoolVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private VotePool pool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Idea idea;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VotePoolVote() {
    }

    public VotePoolVote(VotePool pool, Idea idea, User user) {
        this.pool = pool;
        this.idea = idea;
        this.user = user;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public VotePool getPool() {
        return pool;
    }

    public Idea getIdea() {
        return idea;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
