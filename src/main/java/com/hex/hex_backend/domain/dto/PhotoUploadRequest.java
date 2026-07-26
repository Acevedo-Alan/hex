package com.hex.hex_backend.domain.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class PhotoUploadRequest {
    private UUID playerId;
    private Integer slotIndex;
    private String imageBase64;
}