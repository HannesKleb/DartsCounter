package com.DartsCounter.DartsCounter_backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Frontend läuft auf einem anderen Port (Webserver / Live Server) oder über den Tunnel.
// Alle Ursprünge sind erlaubt, weil keine Cookies benutzt werden: angemeldet wird nur über den
// Authorization-Header, den eine fremde Seite nicht kennt.
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AccountService accountService;
    private final String dataMode;
    private final String servicesMode;

    public WebConfig(AccountService accountService,
                     @Value("${dartscounter.data.require-login:tunnel}") String dataMode,
                     @Value("${dartscounter.services.require-login:tunnel}") String servicesMode) {
        this.accountService = accountService;
        this.dataMode = dataMode;
        this.servicesMode = servicesMode;
    }

    // von außen: Daten nur angemeldet, Kameras nur für Admins
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessGuard(dataMode, false, accountService)).addPathPatterns("/api/data/**");
        registry.addInterceptor(new AccessGuard(servicesMode, true, accountService)).addPathPatterns("/services/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
