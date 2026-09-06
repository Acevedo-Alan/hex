package com.hex.hex_backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.hex.hex_backend.exception.InvalidRoomStateException;
import com.hex.hex_backend.exception.PhotoUploadFailedException;
import com.hex.hex_backend.exception.RateLimitExceededException;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import com.hex.hex_backend.exception.RoomAlreadyStartedException;
import com.hex.hex_backend.exception.RoomCollisionException;
import com.hex.hex_backend.exception.RoomExpiredException;
import com.hex.hex_backend.exception.RoomFullException;
import com.hex.hex_backend.exception.UnauthorizedException;

import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomCollisionException.class)
    public ResponseEntity<?> handleRoomCollision(RoomCollisionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo generar un PIN único. Intente nuevamente."));
    }

    @ExceptionHandler(RoomAlreadyStartedException.class)
    public ResponseEntity<?> handleRoomAlreadyStarted(RoomAlreadyStartedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La partida ya ha comenzado."));
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<?> handleRoomFull(RoomFullException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La sala ya está llena (máximo 8 jugadores)."));
    }

    @ExceptionHandler(InvalidRoomStateException.class)
    public ResponseEntity<?> handleInvalidState(InvalidRoomStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Acción no permitida en el estado actual de la sala."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PhotoUploadFailedException.class)
    public ResponseEntity<?> handlePhotoUploadFailed(PhotoUploadFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Recurso no encontrado. Verifique el PIN de la sala."));
    }

    @ExceptionHandler(RoomExpiredException.class)
    public ResponseEntity<?> handleRoomExpired(RoomExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    // RoomService la usa para validaciones de negocio (no-host intentando
    // iniciar/reiniciar, jugadores no listos) — sin este handler dedicado
    // caían en handleGeneric() y el cliente veía un 500 para lo que en
    // realidad es un 400 (input/estado inválido, no una falla del server).
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ocurrió un error inesperado."));
    }
}