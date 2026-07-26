package com.hex.hex_backend.service;

import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j // Anotación de Lombok para habilitar el logueo
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GridPhotoRepository gridPhotoRepository;

    // Cron: Se ejecuta exactamente en el minuto 0 de cada hora (ej: 14:00, 15:00, 16:00)
    // Para probarlo ahora mismo cada minuto, podrías usar: @Scheduled(cron = "0 * * * * *")
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldRooms() {
        log.info("Iniciando cronjob de limpieza de salas antiguas...");

        // Calculamos el umbral: 3 horas en el pasado
        LocalDateTime threshold = LocalDateTime.now().minusHours(3);
        List<Room> oldRooms = roomRepository.findByCreatedAtBefore(threshold);

        if (oldRooms.isEmpty()) {
            log.info("No se encontraron salas para limpiar.");
            return;
        }

        log.info("Se encontraron {} salas para eliminar.", oldRooms.size());

        gridPhotoRepository.deleteByRoomIn(oldRooms);
        playerRepository.deleteByRoomIn(oldRooms);
        roomRepository.deleteAll(oldRooms);

        log.info("Limpieza de salas completada con éxito.");
    }
}