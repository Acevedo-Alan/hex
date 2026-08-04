package com.hex.hex_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
    List<Player> findByRoomId(UUID roomId);
    void deleteByRoomIn(List<Room> rooms);
    Optional<Player> findFirstByRoomIdAndIdNotOrderByCreatedAtAsc(UUID roomId, UUID excludedPlayerId);
}