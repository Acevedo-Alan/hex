package com.hex.hex_backend.domain.dto;

import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import lombok.Data;

import java.time.LocalDateTime;

// Lo usamos para mandar el estado general sin exponer toda la entidad de BDD
@Data
public class RoomStateResponse {
    private String roomCode;
    private RoomStatus status;
    private String targetHex;
    private LocalDateTime endsAt;

    public static RoomStateResponse fromEntity(Room room) {
        RoomStateResponse response = new RoomStateResponse();
        response.setRoomCode(room.getRoomCode());
        response.setStatus(room.getStatus());
        response.setTargetHex(room.getTargetHex());
        response.setEndsAt(room.getEndsAt());
        return response;
    }
}