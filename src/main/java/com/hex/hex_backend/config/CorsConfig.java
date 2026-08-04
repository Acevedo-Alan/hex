package com.hex.hex_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/v1/**")
                        .allowedOriginPatterns(
                                "http://localhost:*", "https://localhost:*",
                                "http://192.168.*.*:*", "https://192.168.*.*:*",
                                "http://10.*.*.*:*", "https://10.*.*.*:*")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        // Necesario para que el navegador mande/acepte la cookie de sesión
                        // si en algún momento el frontend deja de ser same-origin (hoy pasa
                        // por el proxy de Vite, así que no hace falta, pero no rompe nada
                        // dejarlo prendido). Solo es válido junto con allowedOriginPatterns
                        // (no con allowedOrigins("*")), que es justo lo que ya usamos.
                        .allowCredentials(true);
            }
        };
    }
}