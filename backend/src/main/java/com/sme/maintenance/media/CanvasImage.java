package com.sme.maintenance.media;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.room.Room;

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
        name = "canvas_images",
        indexes = {
                @Index(name = "idx_canvas_images_room", columnList = "room_id")
        }
)
public class CanvasImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;

    @Column(name = "media_id", nullable = false, length = 64)
    private String mediaId;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "pos_x", nullable = false)
    private double x;

    @Column(name = "pos_y", nullable = false)
    private double y;

    @Column(nullable = false)
    private double width;

    @Column(nullable = false)
    private double height;

    @Column(name = "natural_width", nullable = false)
    private int naturalWidth;

    @Column(name = "natural_height", nullable = false)
    private int naturalHeight;

    @Column(name = "z_index", nullable = false)
    private int zIndex = 0;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CanvasImage() {
    }

    public CanvasImage(Room room, String mediaId, String contentType, double x, double y,
                       double width, double height, int naturalWidth, int naturalHeight,
                       int zIndex, User uploadedBy) {
        this.room = room;
        this.mediaId = mediaId;
        this.contentType = contentType;
        this.x = x;
        this.y = y;
        this.width = Math.max(60.0, Math.min(1200.0, width));
        this.height = Math.max(60.0, Math.min(1200.0, height));
        this.naturalWidth = naturalWidth;
        this.naturalHeight = naturalHeight;
        this.zIndex = zIndex;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getContentType() {
        return contentType;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(60.0, Math.min(1200.0, width));
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(60.0, Math.min(1200.0, height));
    }

    public int getNaturalWidth() {
        return naturalWidth;
    }

    public int getNaturalHeight() {
        return naturalHeight;
    }

    public int getZIndex() {
        return zIndex;
    }

    public void setZIndex(int zIndex) {
        this.zIndex = zIndex;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
