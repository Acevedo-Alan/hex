package com.hex.hex_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Emite y valida un token de sesión por jugador: playerId + firma
 * HMAC-SHA256, sin necesidad de guardar nada en la DB (la firma es
 * verificable en el momento). Viaja como cookie HttpOnly — a propósito, y no
 * como header — porque EventSource (usado en /stream) no permite mandar
 * headers custom, pero sí manda cookies solo con que existan.
 */
@Service
public class SessionTokenService {

    public static final String COOKIE_NAME = "hex_session_token";

    private final byte[] secretBytes;

    public SessionTokenService(@Value("${app.session-secret}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID playerId) {
        String payload = playerId.toString();
        return payload + "." + sign(payload);
    }

    /**
     * true si cookieValue es un token válido y corresponde exactamente a
     * expectedPlayerId — no alcanza con que la firma sea válida para
     * *algún* jugador, tiene que ser válida para *este* jugador puntual.
     */
    public boolean isValidFor(String cookieValue, UUID expectedPlayerId) {
        if (cookieValue == null || expectedPlayerId == null) {
            return false;
        }

        int dot = cookieValue.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }

        String payload = cookieValue.substring(0, dot);
        String signature = cookieValue.substring(dot + 1);

        if (!payload.equals(expectedPlayerId.toString())) {
            return false;
        }

        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        // Comparación en tiempo constante — comparar firmas con .equals()
        // normal filtra cuánto del prefijo coincide via timing attack.
        return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de sesión", e);
        }
    }
}