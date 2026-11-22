package com.sme.maintenance.votepool;

import java.time.Instant;
import java.util.UUID;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.room.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_pools")
public class VotePool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "votes_per_participant", nullable = false)
    private int votesPerParticipant = 3;

    @Column(name = "allow_multiple_votes_per_idea", nullable = false)
    private boolean allowMultipleVotesPerIdea = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VotePoolStatus status = VotePoolStatus.OPEN;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected VotePool() {
    }

    public VotePool(Room room, String title, int votesPerParticipant, boolean allowMultipleVotesPerIdea, User createdBy) {
        this.room = room;
        this.title = title != null ? title.trim() : "";
        this.votesPerParticipant = votesPerParticipant > 0 ? votesPerParticipant : 3;
        this.allowMultipleVotesPerIdea = allowMultipleVotesPerIdea;
        this.status = VotePoolStatus.OPEN;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getVotesPerParticipant() {
        return votesPerParticipant;
    }

    public void setVotesPerParticipant(int votesPerParticipant) {
        this.votesPerParticipant = votesPerParticipant;
    }

    public boolean isAllowMultipleVotesPerIdea() {
        return allowMultipleVotesPerIdea;
    }

    public void setAllowMultipleVotesPerIdea(boolean allowMultipleVotesPerIdea) {
        this.allowMultipleVotesPerIdea = allowMultipleVotesPerIdea;
    }

    public VotePoolStatus getStatus() {
        return status;
    }

    public void setStatus(VotePoolStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
