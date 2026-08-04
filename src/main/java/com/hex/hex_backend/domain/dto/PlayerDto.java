package com.hex.hex_backend.domain.dto;

import com.hex.hex_backend.domain.entity.Player;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PlayerDto {
    private UUID id;
    private String nickname;
    private LocalDateTime createdAt;
    private boolean ready;
    private boolean connected;
    private java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;

    public static PlayerDto fromEntity(Player player) {
        PlayerDto dto = new PlayerDto();
        dto.setId(player.getId());
        dto.setNickname(player.getNickname());
        dto.setCreatedAt(player.getCreatedAt());
        dto.setReady(player.isReady());
        dto.setConnected(player.isConnected());
        return dto;
    }
}