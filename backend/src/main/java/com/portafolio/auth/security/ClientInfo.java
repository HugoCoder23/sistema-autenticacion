package com.portafolio.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Datos del cliente para auditoría y para etiquetar sesiones. La IP se toma de la conexión
 * (configura {@code server.forward-headers-strategy} si hay un proxy de confianza delante).
 */
public record ClientInfo(String ipAddress, String userAgent, String deviceLabel) {

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        String userAgent = ua == null ? null : truncate(ua, 200);
        return new ClientInfo(request.getRemoteAddr(), userAgent, describe(ua));
    }

    /** Etiqueta legible tipo "Chrome · Windows" a partir del User-Agent. */
    static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "Dispositivo desconocido";
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") ? "Opera"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari"
                : ua.startsWith("PostmanRuntime") ? "Postman"
                : ua.startsWith("curl") ? "curl"
                : "Cliente HTTP";
        String os = ua.contains("Windows") ? "Windows"
                : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : null;
        return os == null ? browser : browser + " · " + os;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
