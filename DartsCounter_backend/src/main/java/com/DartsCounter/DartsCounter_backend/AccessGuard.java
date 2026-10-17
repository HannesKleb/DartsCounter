package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Schützt Endpunkte vor Zugriffen von außen. Im Heimnetz frei, von außen nur mit Anmeldung.
 *
 * "Von außen" heißt:
 * - Cloudflare Tunnel: Cloudflare hängt an jede Anfrage aus dem Internet "CF-Connecting-IP" an
 *   (cloudflared selbst verbindet sich von 127.0.0.1, deshalb reicht die Adresse allein nicht).
 * - oder die Absender-Adresse ist keine Heimnetz-Adresse (z. B. bei einer Portweiterleitung im Router).
 * Die Adresse wird nur für diese Prüfung angesehen, nie gespeichert oder geloggt.
 */
public class AccessGuard implements HandlerInterceptor {
    static final String TUNNEL_HEADER = "CF-Connecting-IP";

    private final String mode;           // tunnel = nur von außen, always = immer, never = nie
    private final boolean adminOnly;
    private final AccountService accountService;

    public AccessGuard(String mode, boolean adminOnly, AccountService accountService) {
        this.mode = mode;
        this.adminOnly = adminOnly;
        this.accountService = accountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equals(request.getMethod()) || !loginRequired(request)) return true;

        String token = AccountService.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        var account = accountService.authenticate(token);
        if (account.isEmpty()) return reject(response, HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
        if (adminOnly && !AccountService.ROLE_ADMIN.equals(account.get().role())) {
            return reject(response, HttpStatus.FORBIDDEN, "Nur für Admins.");
        }
        return true;
    }

    private boolean loginRequired(HttpServletRequest request) {
        return switch (mode) {
            case "always" -> true;
            case "never" -> false;
            default -> request.getHeader(TUNNEL_HEADER) != null || !isHomeNetwork(request.getRemoteAddr());
        };
    }

    // Loopback, private IPv4 (10/8, 172.16/12, 192.168/16), link-local und IPv6 fc00::/7
    static boolean isHomeNetwork(String remoteAddr) {
        if (remoteAddr == null) return false;
        try {
            InetAddress address = InetAddress.getByName(remoteAddr); // nur IP-Literale, kein DNS
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) return true;
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
        return false;
    }
}
