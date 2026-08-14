package com.hex.hex_backend.domain.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.hex.hex_backend.domain.enums.RoomStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rooms", uniqueConstraints = {
    @UniqueConstraint(columnNames = "room_code", name = "uk_room_code")
})
@Getter @Setter @NoArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    @Column(name = "room_code", nullable = false, length = 5)
    private String roomCode;

    @Column(name = "host_player_id")
    private UUID hostPlayerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.WAITING;

    // Un target por casillero, no uno solo para toda la ronda — si no, el
    // jugador encuentra un objeto que matchea bien y saca la misma foto 9
    // veces. La columna guarda un CSV plano ("#FF0000,#00FF00,...") en vez
    // de una @ElementCollection con tabla aparte — con 9 valores fijos por
    // ronda no vale la pena la tabla hija extra, y el CSV es una columna
    // menos para migrar en un ddl-auto:update.
    @Column(name = "target_hexes", length = 80)
    private String targetHexesRaw;

    public List<String> getTargetHexes() {
        if (targetHexesRaw == null || targetHexesRaw.isBlank()) return List.of();
        return Arrays.asList(targetHexesRaw.split(","));
    }

    public void setTargetHexes(List<String> hexes) {
        this.targetHexesRaw = (hexes == null || hexes.isEmpty()) ? null : String.join(",", hexes);
    }

    @Column(name = "ends_at")
    // Instant, no LocalDateTime: LocalDateTime no lleva zona horaria, y al
    // serializarse a JSON sin ella el navegador interpreta la fecha como
    // hora LOCAL del usuario en vez de UTC — con el server en UTC y un
    // usuario en Argentina (UTC-3), eso desfasaba la cuenta regresiva del
    // Scanner varias horas (el bug del "108725 restantes"). Instant
    // siempre serializa con sufijo Z, sin ambigüedad posible.
    private Instant endsAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;
}