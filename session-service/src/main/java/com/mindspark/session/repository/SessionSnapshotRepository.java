package com.mindspark.session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mindspark.session.entity.SessionSnapshot;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshot, UUID> {

    @Query("SELECT s FROM SessionSnapshot s WHERE " +
           "(s.savedByUserId = :userId OR s.ownerUserId = :userId OR s.participantUserIds LIKE :participantPattern) " +
           "AND (:roomId IS NULL OR s.roomId = :roomId) " +
           "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(:search) OR LOWER(s.roomName) LIKE LOWER(:search)) " +
           "ORDER BY s.createdAt DESC")
    List<SessionSnapshot> findAccessibleSnapshots(
            @Param("userId") String userId,
            @Param("participantPattern") String participantPattern,
            @Param("roomId") String roomId,
            @Param("search") String search);

    @Query("SELECT s FROM SessionSnapshot s WHERE " +
           "(:roomId IS NULL OR s.roomId = :roomId) " +
           "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(:search) OR LOWER(s.roomName) LIKE LOWER(:search)) " +
           "ORDER BY s.createdAt DESC")
    List<SessionSnapshot> findAllSnapshotsAdmin(
            @Param("roomId") String roomId,
            @Param("search") String search);

    List<SessionSnapshot> findByRoomIdOrderByCreatedAtDesc(String roomId);
}
