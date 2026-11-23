package com.portafolio.auth.security;

import com.portafolio.auth.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Cookie del refresh token: HttpOnly (JavaScript no puede leerla, mitiga XSS), SameSite=Strict (un sitio
 * externo no puede hacer que el navegador la envíe, mitiga CSRF) y limitada a {@code /api/auth}.
 * En producción debe ir con {@code Secure} (REFRESH_COOKIE_SECURE=true) detrás de HTTPS.
 */
@Component
public class RefreshCookies {

    public static final String PATH = "/api/auth";

    private final String name;
    private final boolean secure;

    public RefreshCookies(AppProperties props) {
        this.name = props.refresh().cookieName();
        this.secure = props.refresh().cookieSecure();
    }

    public String name() { return name; }

    public ResponseCookie create(String token, Instant expiresAt, Instant now) {
        return base(token).maxAge(Duration.between(now, expiresAt)).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
