package com.sme.maintenance.room;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

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

    @Column(nullable = false)
    private UUID ownerGuestId;

    @Column(nullable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "room_members", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "guest_id", nullable = false)
    private Set<UUID> memberGuestIds = new HashSet<>();

    protected Room() {
    }

    public Room(String name, String invitationCode, UUID ownerGuestId) {
        this.name = name;
        this.invitationCode = invitationCode;
        this.ownerGuestId = ownerGuestId;
        this.createdAt = Instant.now();
        this.memberGuestIds.add(ownerGuestId);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getInvitationCode() { return invitationCode; }
    public UUID getOwnerGuestId() { return ownerGuestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<UUID> getMemberGuestIds() { return memberGuestIds; }
}