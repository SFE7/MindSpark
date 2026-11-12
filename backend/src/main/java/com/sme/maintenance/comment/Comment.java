package com.sme.maintenance.comment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 2000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<Comment> replies = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Comment() {
    }

    public Comment(Idea idea, User author, String content) {
        this(idea, author, content, null);
    }

    public Comment(Idea idea, User author, String content, Comment parentComment) {
        if (idea == null) {
            throw new IllegalArgumentException("Idea is required");
        }
        if (author == null) {
            throw new IllegalArgumentException("Author is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content is required");
        }

        this.idea = idea;
        this.author = author;
        this.content = content.trim();
        this.parentComment = parentComment;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Idea getIdea() {
        return idea;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public String getText() {
        return content;
    }

    public void setContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        this.content = content.trim();
        this.updatedAt = Instant.now();
    }

    public Comment getParentComment() {
        return parentComment;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
