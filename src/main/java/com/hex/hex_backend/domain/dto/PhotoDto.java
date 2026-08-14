package com.hex.hex_backend.domain.dto;

import com.hex.hex_backend.domain.entity.GridPhoto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PhotoDto {
    private UUID id;
    private UUID playerId;
    private Integer slotIndex;
    private String cloudinaryUrl;
    private String cloudinaryPublicId;
    private BigDecimal score;
    private String capturedHex;
    private LocalDateTime createdAt;

    public static PhotoDto fromEntity(GridPhoto photo) {
        PhotoDto dto = new PhotoDto();
        dto.setId(photo.getId());
        dto.setPlayerId(photo.getPlayer().getId());
        dto.setSlotIndex(photo.getSlotIndex());
        dto.setCloudinaryUrl(photo.getCloudinaryUrl());
        dto.setCloudinaryPublicId(photo.getCloudinaryPublicId());
        dto.setScore(photo.getScore());
        dto.setCapturedHex(photo.getCapturedHex());
        dto.setCreatedAt(photo.getCreatedAt());
        return dto;
    }
}