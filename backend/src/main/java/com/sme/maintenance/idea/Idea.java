package com.sme.maintenance.idea;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ideas")
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID authorGuestId;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Idea() {
    }

    public Idea(UUID roomId, UUID authorGuestId, String text) {
        this.roomId = roomId;
        this.authorGuestId = authorGuestId;
        this.text = text;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateText(String text) {
        this.text = text;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getAuthorGuestId() { return authorGuestId; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}