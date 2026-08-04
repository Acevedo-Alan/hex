package com.hex.hex_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    // Viene de app.frontend-url (env var FRONTEND_URL). En local cae al
    // default de application.yml (http://localhost:5174); en producción
    // Render la pisa con la URL real de Vercel.
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/v1/**")
                        .allowedOriginPatterns(
                                // Dev local y LAN, para seguir probando desde el celular en casa.
                                "http://localhost:*", "https://localhost:*",
                                "http://192.168.*.*:*", "https://192.168.*.*:*",
                                "http://10.*.*.*:*", "https://10.*.*.*:*",
                                // Producción: el dominio real del frontend, desde la env var.
                                frontendUrl,
                                // Previews de Vercel (cada PR/branch genera un subdominio propio).
                                "https://*.vercel.app")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowCredentials(true);
            }
        };
    }
}