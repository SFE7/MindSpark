package com.sme.maintenance.media;

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
        name = "idea_attachments",
        indexes = {
                @Index(name = "idx_idea_attachments_idea", columnList = "idea_id"),
                @Index(name = "idx_idea_attachments_room", columnList = "room_id")
        }
)
public class IdeaAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Idea idea;

    @Column(name = "media_id", nullable = false, length = 64)
    private String mediaId;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdeaAttachment() {
    }

    public IdeaAttachment(UUID roomId, Idea idea, String mediaId, String contentType, int width, int height, User uploadedBy) {
        this.roomId = roomId;
        this.idea = idea;
        this.mediaId = mediaId;
        this.contentType = contentType;
        this.width = width;
        this.height = height;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public Idea getIdea() {
        return idea;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getContentType() {
        return contentType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
