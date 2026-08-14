package com.hex.hex_backend.domain.dto;

import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class RoomStateResponse {
    private String roomCode;
    private RoomStatus status;
    private List<String> targetHexes;
    private Instant endsAt;
    private List<PlayerDto> players;
    private List<PhotoDto> photos;
    private UUID hostPlayerId;

    public static RoomStateResponse fromEntity(Room room, List<Player> players, List<GridPhoto> photos) {
        RoomStateResponse response = new RoomStateResponse();
        response.setRoomCode(room.getRoomCode());
        response.setStatus(room.getStatus());
        response.setTargetHexes(room.getTargetHexes());
        response.setEndsAt(room.getEndsAt());

        List<PlayerDto> playerDtos = players.stream().map(PlayerDto::fromEntity).toList();

        // Sumamos el score de todas las fotos de cada jugador para el ranking del podio
        Map<UUID, BigDecimal> totalsByPlayer = photos.stream()
                .filter(p -> p.getScore() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getPlayer().getId(),
                        Collectors.reducing(BigDecimal.ZERO, GridPhoto::getScore, BigDecimal::add)));

        playerDtos.forEach(p -> p.setTotalScore(totalsByPlayer.getOrDefault(p.getId(), BigDecimal.ZERO)));

        response.setPlayers(playerDtos);
        response.setPhotos(photos.stream().map(PhotoDto::fromEntity).toList());
        response.setHostPlayerId(room.getHostPlayerId());
        return response;
    }
}