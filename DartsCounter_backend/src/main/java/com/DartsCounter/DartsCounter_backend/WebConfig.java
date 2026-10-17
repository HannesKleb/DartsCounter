package com.DartsCounter.DartsCounter_backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Frontend läuft auf einem anderen Port (Webserver / Live Server) oder über den Tunnel.
// Alle Ursprünge sind erlaubt, weil keine Cookies benutzt werden: angemeldet wird nur über den
// Authorization-Header, den eine fremde Seite nicht kennt.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
