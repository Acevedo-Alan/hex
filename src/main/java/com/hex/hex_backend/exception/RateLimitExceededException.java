package com.hex.hex_backend.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Demasiadas solicitudes. Esperá un momento y probá de nuevo.");
    }
}