package com.sme.maintenance.drawing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sme.maintenance.room.Room;

@Repository
public interface DrawingRepository extends JpaRepository<Drawing, UUID> {

    List<Drawing> findByRoomAndDeletedFalseOrderByCreatedAtAsc(Room room);

    long countByRoomAndDeletedFalse(Room room);

    Optional<Drawing> findByIdAndRoomAndDeletedFalse(UUID id, Room room);
}
