package com.sme.maintenance.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.entities.User;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Room> findById(UUID id);

    @EntityGraph(attributePaths = "owner")
    Optional<Room> findByInvitationCode(String invitationCode);

    @EntityGraph(attributePaths = "owner")
    List<Room> findByOwnerOrderByCreatedAtDesc(User owner);
}