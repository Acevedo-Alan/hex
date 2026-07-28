package com.hex.hex_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Room;

@Repository
public interface GridPhotoRepository extends JpaRepository<GridPhoto, UUID> {
    List<GridPhoto> findByRoomId(UUID roomId);
    List<GridPhoto> findByRoomIn(List<Room> rooms);
    Optional<GridPhoto> findByPlayerIdAndSlotIndex(UUID playerId, Integer slotIndex);

    void deleteByRoomIn(List<Room> rooms);
}
