package com.hex.hex_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomSseService {

    // Estructura: Map<RoomCode, Map<PlayerId, SseEmitter>>
    private final Map<String, Map<UUID, SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String roomCode, UUID playerId, Object initialSnapshot, Runnable onDisconnect) {
        // Timeout largo (ej. 30 minutos) ya que la partida dura poco.
        SseEmitter emitter = new SseEmitter(1800000L);
        
        roomEmitters.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>()).put(playerId, emitter);

        // Limpieza estricta para evitar Memory Leaks si el usuario cierra la pestaña de golpe
        emitter.onCompletion(() -> { removeEmitter(roomCode, playerId); onDisconnect.run(); });
        emitter.onTimeout(() -> { removeEmitter(roomCode, playerId); onDisconnect.run(); });
        emitter.onError((e) -> { removeEmitter(roomCode, playerId); onDisconnect.run(); });

        try {
            // SNAPSHOT INICIAL: Mandamos el estado actual al conectarse (Soporta F5/Refrescos)
            emitter.send(SseEmitter.event().name("GAME_STATE").data(initialSnapshot));
        } catch (IOException e) {
            removeEmitter(roomCode, playerId);
        }

        return emitter;
    }

    // Para cuando la sesión/sala/jugador no son válidos: en vez de dejar que
    // el controller tire una excepción normal (que Spring no puede resolver
    // en JSON contra un cliente que solo acepta text/event-stream — termina
    // en 500 con body vacío), mandamos un emitter que abre, manda un evento
    // "FATAL_ERROR" con el motivo, y se cierra. El cliente lo recibe como un
    // evento SSE real y puede reaccionar al toque, sin esperar 5 reintentos
    // de red contra un endpoint que siempre va a fallar igual.
    public SseEmitter subscribeWithImmediateError(RuntimeException reason) {
        SseEmitter emitter = new SseEmitter(5000L);
        try {
            emitter.send(SseEmitter.event().name("FATAL_ERROR").data(Map.of(
                    "error", reason.getClass().getSimpleName(),
                    "message", reason.getMessage() != null ? reason.getMessage() : "")));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcastToRoom(String roomCode, String eventName, Object data) {
        Map<UUID, SseEmitter> emitters = roomEmitters.get(roomCode);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        emitters.forEach((playerId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                // Si falla el envío (ej. perdió señal WiFi), limpiamos el emitter muerto
                emitter.completeWithError(e);
                removeEmitter(roomCode, playerId);
            }
        });
    }

    private void removeEmitter(String roomCode, UUID playerId) {
        Map<UUID, SseEmitter> emitters = roomEmitters.get(roomCode);
        if (emitters != null) {
            emitters.remove(playerId);
            if (emitters.isEmpty()) {
                roomEmitters.remove(roomCode);
            }
        }
    }
}