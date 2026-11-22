package com.mindspark.session.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "session_snapshots")
public class SessionSnapshot {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Column(name = "room_name", nullable = false, length = 255)
    private String roomName;

    @Column(name = "invitation_code", length = 50)
    private String invitationCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "saved_by_user_id", nullable = false, length = 128)
    private String savedByUserId;

    @Column(name = "saved_by_name", length = 255)
    private String savedByName;

    @Column(name = "owner_user_id", nullable = false, length = 128)
    private String ownerUserId;

    @Column(name = "participant_user_ids", nullable = false, columnDefinition = "TEXT")
    private String participantUserIds;

    @Column(name = "idea_count")
    private Integer ideaCount;

    @Column(name = "category_count")
    private Integer categoryCount;

    @Column(name = "total_votes")
    private Integer totalVotes;

    @Column(name = "participant_count")
    private Integer participantCount;

    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "TEXT")
    private String snapshotPayload;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
