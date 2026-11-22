package com.sme.maintenance.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sme.maintenance.entities.User;

import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Room> findById(UUID id);

    @EntityGraph(attributePaths = "owner")
    Optional<Room> findByInvitationCode(String invitationCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "owner")
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "owner")
    @Query("SELECT r FROM Room r WHERE r.invitationCode = :invitationCode")
    Optional<Room> findByInvitationCodeWithLock(@Param("invitationCode") String invitationCode);

    @EntityGraph(attributePaths = "owner")
    List<Room> findByOwnerOrderByCreatedAtDesc(User owner);

    long countByOwnerAndClosedFalse(User owner);

    long countByClosedFalse();

    long countByClosedTrue();

    @EntityGraph(attributePaths = "owner")
    List<Room> findTop5ByOrderByCreatedAtDesc();
}