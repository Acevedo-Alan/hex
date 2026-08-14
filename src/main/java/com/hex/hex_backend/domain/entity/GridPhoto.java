package com.hex.hex_backend.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "grid_photos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "slot_index"}, name = "uk_player_slot")
})
@Getter @Setter @NoArgsConstructor
public class GridPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    @Column(name = "cloudinary_url")
    private String cloudinaryUrl;

    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    // Color promedio real que capturó la foto (#RRGGBB) — antes se
    // calculaba (extractAverageColor) solo para el score y se descartaba.
    // Se persiste para poder recolorizar el mosaico de obra de arte del
    // podio con el color de verdad que sacó el jugador, no solo el score.
    @Column(name = "captured_hex", length = 7)
    private String capturedHex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;
}