package com.hex.hex_backend.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

@Service
public class PinGeneratorService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PIN_LENGTH = 5;
    private final SecureRandom random = new SecureRandom();

    public String generatePin() {
        StringBuilder sb = new StringBuilder(PIN_LENGTH);
        for (int i = 0; i < PIN_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
