package com.DartsCounter.DartsCounter_backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Erlaubt dem Frontend (VS Code Live Server) den Zugriff auf alle Endpoints
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${darts.cors.origins}")
    private String[] origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(origins).allowedMethods("GET", "POST");
    }
}
