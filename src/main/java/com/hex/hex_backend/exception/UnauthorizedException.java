package com.hex.hex_backend.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("No estás autorizado para realizar esta acción.");
    }
}