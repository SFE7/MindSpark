
package com.sme.maintenance.vote;

import java.util.UUID;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "votes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_vote_idea_user",
        columnNames = {"idea_id", "user_id"}
    )
)
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Vote() {
    }

    public Vote(Idea idea, User user) {
        if (idea == null) {
            throw new IllegalArgumentException("Idea is required");
        }
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        this.idea = idea;
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public Idea getIdea() {
        return idea;
    }

    public User getUser() {
        return user;
    }
}
