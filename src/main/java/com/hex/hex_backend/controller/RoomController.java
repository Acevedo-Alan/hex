package com.hex.hex_backend.controller;

import com.hex.hex_backend.domain.dto.JoinRoomRequest;
import com.hex.hex_backend.domain.dto.PhotoDto;
import com.hex.hex_backend.domain.dto.PhotoUploadRequest;
import com.hex.hex_backend.domain.dto.PlayerDto;
import com.hex.hex_backend.domain.dto.RoomStateResponse;
import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Player;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.domain.enums.RoomStatus;
import com.hex.hex_backend.exception.InvalidRoomStateException;
import com.hex.hex_backend.exception.RateLimitExceededException;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import com.hex.hex_backend.exception.UnauthorizedException;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import com.hex.hex_backend.service.RateLimiterService;
import com.hex.hex_backend.service.RoomService;
import com.hex.hex_backend.service.RoomSseService;
import com.hex.hex_backend.service.SessionTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
//@CrossOrigin(origins = "${app.frontend-url:http://localhost:5173}")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomSseService sseService;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GridPhotoRepository gridPhotoRepository;
    private final SessionTokenService sessionTokenService;
    private final RateLimiterService rateLimiterService;

    /**
     * IP real del jugador. Con el proxy de Vite en el medio, request.getRemoteAddr()
     * siempre devuelve el IP del propio proceso de Vite (es Vite quien hace
     * el request real al backend) — por eso primero miramos X-Forwarded-For,
     * que el proxy solo agrega si tiene "xfwd: true" en su config.
     */
    private String clientKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Lee la cookie de sesión de la request y confirma que sea válida
     * específicamente para claimedPlayerId — así alguien que conoce el UUID
     * de otro jugador (por ejemplo viéndolo en el tráfico de red) no puede
     * marcarlo listo, subir fotos en su nombre, ni arrancar la partida
     * suplantando al host.
     */
    private void requireOwnership(HttpServletRequest request, UUID claimedPlayerId) {
        Cookie[] cookies = request.getCookies();
        String token = null;
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (SessionTokenService.COOKIE_NAME.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        if (!sessionTokenService.isValidFor(token, claimedPlayerId)) {
            throw new UnauthorizedException();
        }
    }

    @GetMapping("/stats")
public ResponseEntity<java.util.Map<String, Long>> getStats() {
    // Salas que todavía tienen gente jugando o esperando — no cuenta
    // COMPLETED, que igual desaparecen a la hora vía RoomCleanupService.
    long activeRooms = roomRepository.countByStatusIn(List.of(RoomStatus.WAITING, RoomStatus.ACTIVE));
    return ResponseEntity.ok(java.util.Map.of("activeRooms", activeRooms));
}

    @PostMapping
    public ResponseEntity<RoomStateResponse> createRoom(HttpServletRequest httpRequest) {
        if (!rateLimiterService.allow("create:" + clientKey(httpRequest), 10, 10 * 60 * 1000L)) {
            throw new RateLimitExceededException();
        }

        Room room = roomService.createRoom();
        return ResponseEntity.ok(RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId())));
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Player> joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException();
        }

        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("El nickname no puede estar vacío.");
        }

        boolean nicknameTaken = playerRepository.findByRoomId(room.getId()).stream()
                .anyMatch(p -> p.getNickname().equalsIgnoreCase(nickname));
        if (nicknameTaken) {
            throw new IllegalArgumentException("Ese nickname ya está en uso en esta sala.");
        }

        Player player = new Player();
        player.setNickname(nickname);
        player.setRoom(room);
        player = playerRepository.save(player);

        if (room.getHostPlayerId() == null) {
            room.setHostPlayerId(player.getId());
            roomRepository.save(room);
        }

        sseService.broadcastToRoom(roomCode, "PLAYER_JOINED", PlayerDto.fromEntity(player));

        // Cookie HttpOnly, firmada específicamente para este playerId — es lo
        // único que después va a poder marcarlo listo, subir fotos en su
        // nombre, o arrancar la partida si es host. Secure porque todo el
        // proyecto corre sobre HTTPS (mkcert en dev); sin HTTPS el navegador
        // descarta la cookie en silencio.
ResponseCookie cookie = ResponseCookie.from(SessionTokenService.COOKIE_NAME, sessionTokenService.issue(player.getId()))
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .path("/api")
        .maxAge(java.time.Duration.ofHours(4))
        .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(player);
    }

    @GetMapping(path = "/{roomCode}/stream/{playerId}", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUpdates(@PathVariable String roomCode, @PathVariable UUID playerId,
            HttpServletRequest httpRequest) {
        // OJO: este método produce text/event-stream, y EventSource manda
        // Accept: text/event-stream puro (sin comodín ningún tipo JSON). Si
        // dejamos que una excepción se propague hasta el GlobalExceptionHandler
        // normal, Spring intenta negociar un body JSON contra un Accept que
        // solo permite event-stream, no encuentra conversor compatible, y el
        // resultado es un 500 con el body vacío en vez del 401/404 que
        // corresponde — el cliente nunca se entera de qué pasó en realidad.
        // Por eso acá abajo capturamos todo lo que antes tiraba
        // orElseThrow/requireOwnership y lo mandamos como un evento SSE de
        // error real, dentro del mismo content-type que el cliente pidió.
        try {
            requireOwnership(httpRequest, playerId);

            roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(ResourceNotFoundException::new);

            Player player = playerRepository.findById(playerId)
                    .orElseThrow(ResourceNotFoundException::new);

            if (!roomCode.equals(player.getRoom().getRoomCode())) {
                throw new ResourceNotFoundException();
            }
        } catch (UnauthorizedException | ResourceNotFoundException ex) {
            return sseService.subscribeWithImmediateError(ex);
        }

        RoomService.ReconnectResult reconnectResult = roomService.handleReconnect(roomCode, playerId);

        // Si el jugador estaba marcado como desconectado (se cortó a mitad de
        // partida), avisamos al resto de la sala apenas vuelve — su propio
        // snapshot inicial ya se lo manda subscribe() más abajo.
        if (reconnectResult.wasReconnect()) {
            sseService.broadcastToRoom(roomCode, "GAME_STATE", reconnectResult.state());
        }

        return sseService.subscribe(roomCode, playerId, reconnectResult.state(), () -> {
            RoomStateResponse updated = roomService.handleDisconnect(roomCode, playerId);
            if (updated != null) {
                sseService.broadcastToRoom(roomCode, "GAME_STATE", updated);
            }
        });
    }

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<RoomStateResponse> startGame(@PathVariable String roomCode,
            @RequestParam UUID playerId, HttpServletRequest httpRequest) {
        requireOwnership(httpRequest, playerId);

        Room room = roomService.startGame(roomCode, playerId);
        RoomStateResponse response = RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId()));

        sseService.broadcastToRoom(roomCode, "GAME_STARTED", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomCode}/restart")
    public ResponseEntity<RoomStateResponse> restartRoom(@PathVariable String roomCode,
            @RequestParam UUID playerId, HttpServletRequest httpRequest) {
        requireOwnership(httpRequest, playerId);

        Room room = roomService.restartRoom(roomCode, playerId);
        RoomStateResponse response = RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId()));

        // GAME_STATE (no un evento nuevo): el frontend ya sabe reaccionar a
        // este evento y a un status WAITING — reusarlo evita tener que
        // enseñarle un tipo de evento más a useSSE para esto.
        sseService.broadcastToRoom(roomCode, "GAME_STATE", response);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{roomCode}/players/{playerId}/ready")
    public ResponseEntity<RoomStateResponse> setReady(@PathVariable String roomCode,
            @PathVariable UUID playerId, @RequestBody com.hex.hex_backend.domain.dto.ReadyRequest request,
            HttpServletRequest httpRequest) {
        requireOwnership(httpRequest, playerId);

        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(ResourceNotFoundException::new);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(ResourceNotFoundException::new);

        player.setReady(request.isReady());
        playerRepository.save(player);

        RoomStateResponse response = RoomStateResponse.fromEntity(
                room,
                playerRepository.findByRoomId(room.getId()),
                gridPhotoRepository.findByRoomId(room.getId()));

        sseService.broadcastToRoom(roomCode, "GAME_STATE", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomCode}/photos")
public ResponseEntity<PhotoDto> submitPhoto(@PathVariable String roomCode,
            @RequestBody PhotoUploadRequest request, HttpServletRequest httpRequest) {
        requireOwnership(httpRequest, request.getPlayerId());

        if (!rateLimiterService.allow("photo:" + request.getPlayerId(), 30, 60 * 1000L)) {
            throw new RateLimitExceededException();
        }

        GridPhoto photo = roomService.submitPhoto(
                roomCode,
                request.getPlayerId(),
                request.getSlotIndex(),
                request.getImageBase64());

        PhotoDto photoDto = PhotoDto.fromEntity(photo);
        sseService.broadcastToRoom(roomCode, "PHOTO_UPLOADED", photoDto);

        // Si esa foto hizo que la sala se completara (ej. único jugador termina su última casilla),
        // avisamos el nuevo estado para que el frontend redirija solo al podio.
        Room room = roomRepository.findByRoomCode(roomCode).orElseThrow(ResourceNotFoundException::new);
        if (room.getStatus() == RoomStatus.COMPLETED) {
            RoomStateResponse response = RoomStateResponse.fromEntity(
                    room,
                    playerRepository.findByRoomId(room.getId()),
                    gridPhotoRepository.findByRoomId(room.getId()));
            sseService.broadcastToRoom(roomCode, "GAME_STATE", response);
        }

        return ResponseEntity.ok(photoDto);
    }
}