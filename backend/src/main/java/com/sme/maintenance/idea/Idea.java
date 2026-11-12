
package com.sme.maintenance.idea;

import java.time.Instant;
import java.util.UUID;

import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.room.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ideas")
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    //foreign key relationship with category since ideas can be categorizeed in the second phase
    @ManyToOne(fetch = FetchType.LAZY) //kept optional = true which is the default since ideas can also be created without categories
    @JoinColumn(name = "category_id", nullable = true) //same thing with nullable
    private Category category;

    protected Idea() {
    }

    public Idea(Room room, User author, String text) {
        this(room, author, text, text, null, null, null, null,null);
    }

    public Idea(
            Room room,
            User author,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Category category
        ) {

        if (room == null) {
            throw new IllegalArgumentException("Room is required");
        }
        if (author == null) {
            throw new IllegalArgumentException("Author is required");
        }

        this.room = room;
        this.author = author;

        this.title = title != null && !title.isBlank()
                ? title.trim()
                : (text != null ? text.trim() : "");

        this.description = description != null ? description.trim() : "";

        this.text = text != null && !text.isBlank()
                ? text.trim()
                : this.title;

        this.x = x != null ? x : 500.0;
        this.y = y != null ? y : 250.0;
        this.color = color != null && !color.isBlank()
                ? color
                : "#9fc9a9";

        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.category = category;
    }

    public void update(
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Category category

        ) {

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


        if (category != null && !category.getRoom().getId().equals(this.room.getId())) {
        throw new IllegalArgumentException("The selected category does not belong to this room.");
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
        this.category = category;
    }

    public void updatePosition(Double x, Double y) {
        if (x != null) {
            this.x = x;
        }

        if (y != null) {
            this.y = y;
        }

        this.updatedAt = Instant.now();
    }

    public void update(String text, String title, String description) {
        update(text, title, description, null, null, null,null);
    }

    public void updateText(String text) {
        update(text, text, this.description, null, null, null,null);
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public User getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public String getTitle() {
        return title != null && !title.isBlank() ? title : text;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public Double getX() {
        return x != null ? x : 500.0;
    }

    public Double getY() {
        return y != null ? y : 250.0;
    }

    public String getColor() {
        return color != null && !color.isBlank() ? color : "#9fc9a9";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Category getCategory(){
        return category;
    }
}
