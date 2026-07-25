package com.hex.hex_backend.service;

import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import com.hex.hex_backend.exception.InvalidRoomStateException;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import com.hex.hex_backend.exception.RoomAlreadyStartedException;
import com.hex.hex_backend.exception.RoomCollisionException;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GridPhotoRepository gridPhotoRepository;
    private final PinGeneratorService pinGenerator;
    private final ColorMathService colorMath;

    @Transactional
    public Room createRoom() {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Room room = new Room();
                room.setRoomCode(pinGenerator.generatePin());
                room.setStatus(RoomStatus.WAITING);
                return roomRepository.save(room);
            } catch (DataIntegrityViolationException e) {
                if (i == maxRetries - 1) {
                    throw new RoomCollisionException();
                }
            }
        }
        throw new RoomCollisionException();
    }

    @Transactional
    public Room startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException();
        }

        try {
            room.setStatus(RoomStatus.ACTIVE);
            room.setTargetHex(generateRandomHex());
            room.setEndsAt(LocalDateTime.now().plusSeconds(90)); 
            
            return roomRepository.save(room);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new RoomAlreadyStartedException();
        }
    }

    @Transactional
    public GridPhoto submitPhoto(String roomCode, UUID playerId, int slotIndex, String base64Payload) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(ResourceNotFoundException::new);

        if (room.getStatus() != RoomStatus.ACTIVE || LocalDateTime.now().isAfter(room.getEndsAt())) {
            throw new InvalidRoomStateException();
        }

        int[] photoRgb = extractAverageColor(base64Payload);
        int[] targetRgb = colorMath.hexToRgb(room.getTargetHex());
        BigDecimal officialScore = colorMath.calculateMatchScore(targetRgb, photoRgb);

        GridPhoto photo = gridPhotoRepository.findByPlayerIdAndSlotIndex(playerId, slotIndex)
                .orElse(new GridPhoto());

        photo.setPlayer(player);
        photo.setRoom(room);
        photo.setSlotIndex(slotIndex);
        photo.setScore(officialScore);
        
        // TODO: En Fase 3, subiremos el base64 a Cloudinary aquí.

        return gridPhotoRepository.save(photo);
    }

    private int[] extractAverageColor(String base64Payload) {
        try {
            String cleanBase64 = base64Payload.contains(",") ? base64Payload.split(",")[1] : base64Payload;
            byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
            
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                throw new IllegalArgumentException("Payload is not a valid image format.");
            }

            int centerX = img.getWidth() / 2;
            int centerY = img.getHeight() / 2;
            int size = 10;
            
            int startX = Math.max(0, centerX - size / 2);
            int startY = Math.max(0, centerY - size / 2);
            int endX = Math.min(img.getWidth(), startX + size);
            int endY = Math.min(img.getHeight(), startY + size);

            long r = 0, g = 0, b = 0;
            int count = 0;
            
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    int rgb = img.getRGB(x, y);
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                    count++;
                }
            }
            
            if (count == 0) return new int[]{0, 0, 0};
            return new int[]{ (int)(r/count), (int)(g/count), (int)(b/count) };
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode image payload", e);
        }
    }

    private String generateRandomHex() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        int r = random.nextInt(256);
        int g = random.nextInt(256);
        int b = random.nextInt(256);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}