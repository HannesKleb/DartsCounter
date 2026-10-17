package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Schützt /api/data: im Heimnetz frei, über den Cloudflare Tunnel nur mit Anmeldung.
// Cloudflare hängt an jede Anfrage aus dem Internet den Header "CF-Connecting-IP" an; ein Nutzer kann ihn
// nicht entfernen. Geprüft wird nur, OB es ihn gibt, die IP selbst wird weder gelesen noch gespeichert.
@Component
public class DataAccessInterceptor implements HandlerInterceptor {
    static final String TUNNEL_HEADER = "CF-Connecting-IP";

    // tunnel = nur über den Tunnel Anmeldung nötig, always = immer, never = nie
    private final String mode;
    private final AccountService accountService;

    public DataAccessInterceptor(@Value("${dartscounter.data.require-login:tunnel}") String mode, AccountService accountService) {
        this.mode = mode;
        this.accountService = accountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equals(request.getMethod()) || !loginRequired(request)) return true;
        String token = AccountService.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (accountService.authenticate(token).isPresent()) return true;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Bitte anmelden.\"}");
        return false;
    }

    private boolean loginRequired(HttpServletRequest request) {
        return switch (mode) {
            case "always" -> true;
            case "never" -> false;
            default -> request.getHeader(TUNNEL_HEADER) != null;
        };
    }
}
