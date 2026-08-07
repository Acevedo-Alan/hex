package com.hex.hex_backend.exception;

public class PhotoUploadFailedException extends RuntimeException {
    public PhotoUploadFailedException() {
        super("No se pudo subir la foto. Probá de nuevo.");
    }
}
