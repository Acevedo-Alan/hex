package com.hex.hex_backend.exception;

public class RoomExpiredException extends RuntimeException {
    public RoomExpiredException() {
        super("La sala ha expirado o la partida ya finalizó.");
    }
}
