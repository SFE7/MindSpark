package com.sme.maintenance.vote;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(columnNames = { "idea_id", "guest_id" }))
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idea_id", nullable = false)
    private UUID ideaId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    protected Vote() {
    }

    public Vote(UUID ideaId, UUID guestId) {
        this.ideaId = ideaId;
        this.guestId = guestId;
    }

    public UUID getIdeaId() { return ideaId; }
    public UUID getGuestId() { return guestId; }
}