package com.hex.hex_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hex.hex_backend.domain.entity.GridPhoto;
import com.hex.hex_backend.domain.entity.Room;
import com.hex.hex_backend.repository.GridPhotoRepository;
import com.hex.hex_backend.repository.PlayerRepository;
import com.hex.hex_backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GridPhotoRepository gridPhotoRepository;
    private final Cloudinary cloudinary;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldRooms() {
        log.info("Iniciando cronjob de limpieza de salas antiguas...");

        LocalDateTime threshold = LocalDateTime.now().minusHours(3);
        List<Room> oldRooms = roomRepository.findByCreatedAtBefore(threshold);

        if (oldRooms.isEmpty()) {
            log.info("No se encontraron salas para limpiar.");
            return;
        }

        log.info("Se encontraron {} salas para eliminar.", oldRooms.size());

        List<GridPhoto> photosToDelete = gridPhotoRepository.findByRoomIn(oldRooms);
        photosToDelete.forEach(photo -> {
            try {
                String publicId = resolvePublicId(photo);
                if (StringUtils.hasText(publicId)) {
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                }
            } catch (Exception e) {
                log.warn("No se pudo borrar la imagen {} de Cloudinary", photo.getId(), e);
            }
        });

        gridPhotoRepository.deleteByRoomIn(oldRooms);
        playerRepository.deleteByRoomIn(oldRooms);
        roomRepository.deleteAll(oldRooms);

        log.info("Limpieza de salas completada con éxito.");
    }

    private String resolvePublicId(GridPhoto photo) {
        if (StringUtils.hasText(photo.getCloudinaryPublicId())) {
            return photo.getCloudinaryPublicId();
        }

        if (!StringUtils.hasText(photo.getCloudinaryUrl())) {
            return null;
        }

        try {
            URI uri = new URI(photo.getCloudinaryUrl());
            String path = uri.getPath();
            if (!path.contains("/upload/")) {
                return null;
            }

            String[] segments = path.split("/");
            int uploadIndex = -1;
            for (int i = 0; i < segments.length; i++) {
                if ("upload".equals(segments[i])) {
                    uploadIndex = i;
                    break;
                }
            }

            if (uploadIndex < 0 || uploadIndex + 1 >= segments.length) {
                return null;
            }

            int startIndex = uploadIndex + 1;
            if (segments[startIndex].matches("v\\d+")) {
                startIndex++;
            }

            StringBuilder publicId = new StringBuilder();
            for (int i = startIndex; i < segments.length; i++) {
                String segment = segments[i];
                if (StringUtils.hasText(segment)) {
                    if (publicId.length() > 0) {
                        publicId.append('/');
                    }
                    publicId.append(segment);
                }
            }

            if (publicId.length() == 0) {
                return null;
            }

            return publicId.toString().replaceFirst("\\.[^.]+$", "");
        } catch (URISyntaxException e) {
            return null;
        }
    }
}