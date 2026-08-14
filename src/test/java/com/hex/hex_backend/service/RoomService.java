package com.hex.hex_backend.service;

import com.hex.hex_backend.domain.dto.RoomStateResponse;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import com.hex.hex_backend.exception.InvalidRoomStateException;
import com.hex.hex_backend.exception.PhotoUploadFailedException;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import com.hex.hex_backend.exception.RoomAlreadyStartedException;
import com.hex.hex_backend.exception.RoomCollisionException;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GridPhotoRepository gridPhotoRepository;
    private final PinGeneratorService pinGenerator;
    private final ColorMathService colorMath;
    private final Cloudinary cloudinary;
    private final RoomSseService sseService;
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
    public Room startGame(String roomCode, UUID requestingPlayerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException();
        }

        if (!requestingPlayerId.equals(room.getHostPlayerId())) {
            throw new IllegalStateException("Solo el dueño de la sala puede iniciar la partida.");
        }

        java.util.List<Player> players = playerRepository.findByRoomId(room.getId());
        boolean allReady = !players.isEmpty() && players.stream().allMatch(Player::isReady);
        if (!allReady) {
            throw new IllegalStateException("Todos los jugadores deben estar listos para iniciar.");
        }

        try {
            room.setStatus(RoomStatus.ACTIVE);
            // 9 targets, uno por casillero — antes era un solo color para
            // toda la ronda, y bastaba con encontrar un objeto que matcheara
            // bien y sacarle la misma foto 9 veces. Con un color distinto
            // por foto, cada casillero obliga a salir a buscar de nuevo.
            room.setTargetHexes(generateTargetPalette(9));
            // 150s en vez de 90: encontrar un objeto nuevo para cada uno de
            // los 9 colores es bastante más lento que reencuadrar el mismo
            // objeto 9 veces — el tiempo total tiene que acompañar.
            room.setEndsAt(Instant.now().plusSeconds(150));
            
            return roomRepository.save(room);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new RoomAlreadyStartedException();
        }
    }
    @Transactional
    public Room restartRoom(String roomCode, UUID requestingPlayerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);

        if (room.getStatus() != RoomStatus.COMPLETED) {
            throw new InvalidRoomStateException();
        }

        if (!requestingPlayerId.equals(room.getHostPlayerId())) {
            throw new IllegalStateException("Solo el dueño de la sala puede iniciar la revancha.");
        }

        List<Player> players = playerRepository.findByRoomId(room.getId());

        // Mismo criterio que handleDisconnect en una sala WAITING: quien
        // terminó desconectado se saca de la sala en vez de arrastrarlo a la
        // revancha — no podría marcarse "ready" y dejaría el lobby trabado
        // para siempre. Si el host estaba entre ellos, le pasamos la posta
        // al jugador conectado más antiguo (en la práctica no debería pasar,
        // porque quien llama a este método ya es el host y por lo tanto
        // está conectado).
        for (Player p : players) {
            if (!p.isConnected()) {
                boolean wasHost = p.getId().equals(room.getHostPlayerId());
                playerRepository.delete(p);
                if (wasHost) {
                    UUID newHostId = playerRepository
                            .findFirstByRoomIdAndIdNotOrderByCreatedAtAsc(room.getId(), p.getId())
                            .map(Player::getId)
                            .orElse(null);
                    room.setHostPlayerId(newHostId);
                }
            }
        }

        List<Player> remaining = playerRepository.findByRoomId(room.getId());
        if (remaining.isEmpty() || room.getHostPlayerId() == null) {
            throw new InvalidRoomStateException();
        }

        remaining.forEach(p -> p.setReady(false));
        playerRepository.saveAll(remaining);

        // Fotos de la ronda anterior — la revancha arranca con grilla
        // vacía. El totalScore no vive en Player, se recalcula solo al
        // sumar GridPhoto (ver RoomStateResponse.fromEntity), así que no
        // hace falta tocar nada de scores acá.
        gridPhotoRepository.deleteAll(gridPhotoRepository.findByRoomId(room.getId()));

        room.setStatus(RoomStatus.WAITING);
        room.setTargetHexes(null);
        room.setEndsAt(null);
        return roomRepository.save(room);
    }

@org.springframework.scheduling.annotation.Scheduled(fixedRate = 5000)
    @Transactional
    public void completeExpiredRooms() {
        List<Room> activeRooms = roomRepository.findByStatus(RoomStatus.ACTIVE);

        for (Room room : activeRooms) {
            if (room.getEndsAt() != null && Instant.now().isAfter(room.getEndsAt())) {
                room.setStatus(RoomStatus.COMPLETED);
                roomRepository.save(room);

                RoomStateResponse response = RoomStateResponse.fromEntity(
                        room,
                        playerRepository.findByRoomId(room.getId()),
                        gridPhotoRepository.findByRoomId(room.getId()));

                sseService.broadcastToRoom(room.getRoomCode(), "GAME_STATE", response);
            }
        }
    }
    @Transactional
    public RoomStateResponse handleDisconnect(String roomCode, UUID playerId) {
        Room room = roomRepository.findByRoomCode(roomCode).orElse(null);
        if (room == null) return null;
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) return null;

        if (room.getStatus() == RoomStatus.WAITING) {
            boolean wasHost = playerId.equals(room.getHostPlayerId());
            playerRepository.delete(player);

            if (wasHost) {
                UUID newHostId = playerRepository
                        .findFirstByRoomIdAndIdNotOrderByCreatedAtAsc(room.getId(), playerId)
                        .map(Player::getId)
                        .orElse(null);
                room.setHostPlayerId(newHostId);
                roomRepository.save(room);
            }
        } else {
            player.setConnected(false);
            playerRepository.save(player);
        }

        return RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId()));
    }

    /**
     * Se llama cada vez que se abre (o reabre) el stream SSE de un jugador.
     * Si venía marcado como desconectado (se cortó a mitad de partida y
     * volvió), lo repone. wasReconnect indica si hubo que tocar algo, para
     * que el controller decida si vale la pena avisarle al resto de la sala.
     */
    @Transactional
    public ReconnectResult handleReconnect(String roomCode, UUID playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(ResourceNotFoundException::new);

        boolean wasReconnect = !player.isConnected();
        if (wasReconnect) {
            player.setConnected(true);
            playerRepository.save(player);
        }

        RoomStateResponse response = RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId()));

        return new ReconnectResult(response, wasReconnect);
    }

    public record ReconnectResult(RoomStateResponse state, boolean wasReconnect) {
    }
    @Transactional
    public GridPhoto submitPhoto(String roomCode, UUID playerId, int slotIndex, String base64Payload) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(ResourceNotFoundException::new);

        if (room.getStatus() != RoomStatus.ACTIVE || Instant.now().isAfter(room.getEndsAt())) {
            throw new InvalidRoomStateException();
        }

        int[] photoRgb = extractAverageColor(base64Payload);
        List<String> targetHexes = room.getTargetHexes();
        if (slotIndex < 0 || slotIndex >= targetHexes.size()) {
            throw new InvalidRoomStateException();
        }
        int[] targetRgb = colorMath.hexToRgb(targetHexes.get(slotIndex));
        BigDecimal officialScore = colorMath.calculateMatchScore(targetRgb, photoRgb);

        GridPhoto photo = gridPhotoRepository.findByPlayerIdAndSlotIndex(playerId, slotIndex)
                .orElse(new GridPhoto());

        photo.setPlayer(player);
        photo.setRoom(room);
        photo.setSlotIndex(slotIndex);
        photo.setScore(officialScore);

        try {
            String cleanBase64 = base64Payload.contains(",") ? base64Payload.split(",")[1] : base64Payload;
            byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
            Map uploadResult = cloudinary.uploader().upload(imageBytes, ObjectUtils.emptyMap());
            photo.setCloudinaryUrl((String) uploadResult.get("secure_url"));
            photo.setCloudinaryPublicId((String) uploadResult.get("public_id"));
        } catch (Exception e) {
            // Antes esto solo logueaba un warning y seguía de largo: la foto
            // quedaba guardada en la base con cloudinaryUrl null, el
            // endpoint devolvía 200 igual, y el frontend avanzaba nextSlot
            // como si el slot estuviera completo. El jugador quedaba con un
            // casillero roto sin foto y sin forma de reintentarlo, porque
            // nextSlot nunca retrocede. Ahora se propaga la excepción: el
            // @Transactional hace rollback (no se guarda nada a medias) y el
            // frontend recibe un error real, que ScannerView ya sabe manejar
            // (toast "ERROR AL ENVIAR" + no avanza de slot).
            log.error("No se pudo subir la imagen a Cloudinary para el jugador {} (slot {})", playerId, slotIndex, e);
            throw new PhotoUploadFailedException();
        }

        GridPhoto savedPhoto = gridPhotoRepository.save(photo);
        checkForEarlyCompletion(room);
        return savedPhoto;
    }

    private void checkForEarlyCompletion(Room room) {
        if (room.getStatus() != RoomStatus.ACTIVE) return;

        List<Player> players = playerRepository.findByRoomId(room.getId());
        List<GridPhoto> photos = gridPhotoRepository.findByRoomId(room.getId());

        boolean everyoneFinished = !players.isEmpty() && players.stream()
                .allMatch(p -> photos.stream()
                        .filter(ph -> ph.getPlayer().getId().equals(p.getId()))
                        .count() >= 9);

        if (everyoneFinished) {
            room.setStatus(RoomStatus.COMPLETED);
            roomRepository.save(room);
        }
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

    // Genera `count` colores repartidos parejo en el círculo cromático (cada
    // 360/count grados) a partir de un punto de arranque al azar, en vez de
    // `count` tiradas random independientes — random puro puede darte 3
    // tonos de azul casi iguales y ningún verde en toda la ronda. Satura-
    // ción y luminosidad quedan acotadas (55-90% / 40-65%) para que el
    // color siga siendo "encontrable": nada casi blanco, casi negro, o tan
    // desaturado que se vea gris.
    private List<String> generateTargetPalette(int count) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        double startHue = random.nextDouble() * 360;
        List<String> palette = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double hue = (startHue + i * (360.0 / count)) % 360;
            double saturation = 0.55 + random.nextDouble() * 0.35;
            double lightness = 0.40 + random.nextDouble() * 0.25;
            palette.add(hslToHex(hue, saturation, lightness));
        }
        return palette;
    }

    private String hslToHex(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = (int) Math.round((r + m) * 255);
        int gi = (int) Math.round((g + m) * 255);
        int bi = (int) Math.round((b + m) * 255);
        return String.format("#%02X%02X%02X", ri, gi, bi);
    }
}