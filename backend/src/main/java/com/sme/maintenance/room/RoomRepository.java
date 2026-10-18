package com.sme.maintenance.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByInvitationCode(String invitationCode);
    List<Room> findByOwnerGuestIdOrderByCreatedAtDesc(UUID ownerGuestId);
}