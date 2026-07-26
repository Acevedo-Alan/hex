package com.hex.hex_backend.controller;

import com.hex.hex_backend.domain.dto.JoinRoomRequest;
import com.hex.hex_backend.domain.dto.PhotoUploadRequest;
import com.hex.hex_backend.domain.dto.RoomStateResponse;
import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import com.hex.hex_backend.service.RoomService;
import com.hex.hex_backend.service.RoomSseService;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@CrossOrigin(origins = "*") // Permite que el frontend de React se conecte
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomSseService sseService;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;

    @PostMapping
    public ResponseEntity<RoomStateResponse> createRoom() {
        Room room = roomService.createRoom();
        return ResponseEntity.ok(RoomStateResponse.fromEntity(room));
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Player> joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);

        Player player = new Player();
        player.setNickname(request.getNickname());
        player.setRoom(room);
        player = playerRepository.save(player);

        sseService.broadcastToRoom(roomCode, "PLAYER_JOINED", player);
        return ResponseEntity.ok(player);
    }

    @GetMapping(path = "/{roomCode}/stream/{playerId}", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUpdates(@PathVariable String roomCode, @PathVariable UUID playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);

        RoomStateResponse snapshot = RoomStateResponse.fromEntity(room);
        return sseService.subscribe(roomCode, playerId, snapshot);
    }

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<RoomStateResponse> startGame(@PathVariable String roomCode) {
        Room room = roomService.startGame(roomCode);
        RoomStateResponse response = RoomStateResponse.fromEntity(room);

        sseService.broadcastToRoom(roomCode, "GAME_STARTED", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomCode}/photos")
    public ResponseEntity<GridPhoto> submitPhoto(@PathVariable String roomCode,
            @RequestBody PhotoUploadRequest request) {
        GridPhoto photo = roomService.submitPhoto(
                roomCode,
                request.getPlayerId(),
                request.getSlotIndex(),
                request.getImageBase64());

        sseService.broadcastToRoom(roomCode, "PHOTO_UPLOADED", photo);
        return ResponseEntity.ok(photo);
    }
}