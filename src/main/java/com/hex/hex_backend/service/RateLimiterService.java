package com.hex.hex_backend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter por sliding window, en memoria, sin librerías externas — no
 * podía verificar que una dependencia nueva (bucket4j, etc.) resolviera bien
 * sin acceso a Maven Central para probar, así que esto es deliberadamente
 * simple: guarda los timestamps de los últimos hits por key y descarta los
 * que ya salieron de la ventana.
 *
 * No sobrevive un restart del backend (en memoria) y no se comparte entre
 * instancias si algún día corre más de una — para el tamaño de este
 * proyecto (una sola instancia, juego casual en LAN) es una simplificación
 * razonable.
 */
@Service
public class RateLimiterService {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * true si esta key todavía entra dentro de maxRequests en windowMillis
     * — y la registra. false si hay que rechazar la request.
     */
    public synchronized boolean allow(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());

        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}