package com.hex.hex_backend.domain.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "players")
@Getter @Setter @NoArgsConstructor
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String nickname;
    @Column(nullable = false)
    private boolean ready = false;
    @Column(nullable = false)
    private boolean connected = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;
}
