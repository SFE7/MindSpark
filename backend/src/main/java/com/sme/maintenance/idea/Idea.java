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

    @Column(length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "pos_x")
    private Double x;

    @Column(name = "pos_y")
    private Double y;

    @Column(length = 32)
    private String color;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Idea() {
    }

    public Idea(UUID roomId, UUID authorGuestId, String text) {
        this(roomId, authorGuestId, text, text, null, null, null, null);
    }

    public Idea(UUID roomId, UUID authorGuestId, String text, String title, String description, Double x, Double y, String color) {
        this.roomId = roomId;
        this.authorGuestId = authorGuestId;
        this.title = title != null && !title.isBlank() ? title.trim() : (text != null ? text.trim() : "");
        this.description = description != null ? description.trim() : "";
        this.text = text != null && !text.isBlank() ? text.trim() : this.title;
        this.x = x != null ? x : 500.0;
        this.y = y != null ? y : 250.0;
        this.color = color != null && !color.isBlank() ? color : "#9fc9a9";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String text, String title, String description, Double x, Double y, String color) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.trim();
        }
        if (text != null && !text.isBlank()) {
            this.text = text.trim();
        } else if (this.title != null) {
            this.text = this.title;
        }
        if (x != null) {
            this.x = x;
        }
        if (y != null) {
            this.y = y;
        }
        if (color != null && !color.isBlank()) {
            this.color = color;
        }
        this.updatedAt = Instant.now();
    }

    public void updatePosition(Double x, Double y) {
        if (x != null) this.x = x;
        if (y != null) this.y = y;
        this.updatedAt = Instant.now();
    }

    public void update(String text, String title, String description) {
        update(text, title, description, null, null, null);
    }

    public void updateText(String text) {
        update(text, text, this.description, null, null, null);
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getAuthorGuestId() { return authorGuestId; }
    public String getText() { return text; }
    public String getTitle() { return title != null && !title.isBlank() ? title : text; }
    public String getDescription() { return description != null ? description : ""; }
    public Double getX() { return x != null ? x : 500.0; }
    public Double getY() { return y != null ? y : 250.0; }
    public String getColor() { return color != null && !color.isBlank() ? color : "#9fc9a9"; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}