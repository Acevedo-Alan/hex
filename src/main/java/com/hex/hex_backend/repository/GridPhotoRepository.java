package com.hex.hex_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hex.hex_backend.domain.entity.GridPhoto;

@Repository
public interface GridPhotoRepository extends JpaRepository<GridPhoto, UUID> {
    List<GridPhoto> findByRoomId(UUID roomId);
    Optional<GridPhoto> findByPlayerIdAndSlotIndex(UUID playerId, Integer slotIndex);
}
