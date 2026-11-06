
package com.sme.maintenance.room;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.sme.maintenance.entities.User;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 8)
    private String invitationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(
        name = "room_members",
        joinColumns = @JoinColumn(name = "room_id"),
        uniqueConstraints = @UniqueConstraint(
            columnNames = {"room_id", "user_id"}
        )
    )
    @Column(name = "user_id", nullable = false)
    private Set<Long> memberUserIds = new HashSet<>();

    protected Room() {}

    public Room(String name, String invitationCode, User owner) {
        this.name = name;
        this.invitationCode = invitationCode;
        this.owner = owner;
        this.createdAt = Instant.now();

        if (owner.getId() != null) {
            this.memberUserIds.add(owner.getId());
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInvitationCode() {
        return invitationCode;
    }

    public User getOwner() {
        return owner;
    }

    public Long getOwnerId() {
        return owner.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Long> getMemberIds() {
        return memberUserIds;
    }
}
