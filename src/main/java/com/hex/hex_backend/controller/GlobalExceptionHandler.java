package com.hex.hex_backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.hex.hex_backend.exception.InvalidRoomStateException;
import com.hex.hex_backend.exception.ResourceNotFoundException;
import com.hex.hex_backend.exception.RoomAlreadyStartedException;
import com.hex.hex_backend.exception.RoomCollisionException;

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

    @ExceptionHandler(InvalidRoomStateException.class)
    public ResponseEntity<?> handleInvalidState(InvalidRoomStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Acción no permitida en el estado actual de la sala."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Recurso no encontrado. Verifique el PIN de la sala."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
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