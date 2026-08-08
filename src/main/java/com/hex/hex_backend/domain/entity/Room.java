package com.hex.hex_backend.domain.entity;

import java.time.Instant;
import java.time.LocalDateTime;
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

    @Column(name = "target_hex", length = 7)
    private String targetHex;

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