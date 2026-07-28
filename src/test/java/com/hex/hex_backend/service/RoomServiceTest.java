package com.hex.hex_backend.service;

import com.cloudinary.Cloudinary;
import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GridPhotoRepository gridPhotoRepository;

    @Mock
    private PinGeneratorService pinGenerator;

    @Mock
    private ColorMathService colorMath;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private com.cloudinary.Uploader uploader;

    @InjectMocks
    private RoomService roomService;

    @Test
    void submitPhotoShouldUploadToCloudinaryAndPersistMetadata() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String roomCode = "ABC12";
        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setStatus(RoomStatus.ACTIVE);
        room.setTargetHex("#123456");
        room.setEndsAt(LocalDateTime.now().plusMinutes(5));

        Player player = new Player();
        player.setNickname("Alan");
        player.setRoom(room);

        when(roomRepository.findByRoomCode(roomCode)).thenReturn(Optional.of(room));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(colorMath.hexToRgb("#123456")).thenReturn(new int[]{1, 2, 3});
        when(colorMath.calculateMatchScore(new int[]{1, 2, 3}, new int[]{127, 127, 127})).thenReturn(java.math.BigDecimal.TEN);
        when(gridPhotoRepository.findByPlayerIdAndSlotIndex(playerId, 2)).thenReturn(Optional.empty());

        Map<String, Object> uploadResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/test-image.jpg",
                "public_id", "hexa/test-image"
        );
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);
        when(gridPhotoRepository.save(any(GridPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        image.setRGB(1, 0, 0x00FF00);
        image.setRGB(0, 1, 0x0000FF);
        image.setRGB(1, 1, 0xFFFFFF);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        String imageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());

        GridPhoto result = roomService.submitPhoto(roomCode, playerId, 2, imageBase64);

        assertEquals("https://res.cloudinary.com/demo/image/upload/test-image.jpg", result.getCloudinaryUrl());
        assertEquals("hexa/test-image", result.getCloudinaryPublicId());
        verify(gridPhotoRepository).save(any(GridPhoto.class));
    }
}
