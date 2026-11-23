package com.portafolio.auth.controller;

import com.portafolio.auth.dto.Requests.ForgotPasswordRequest;
import com.portafolio.auth.dto.Requests.LoginRequest;
import com.portafolio.auth.dto.Requests.RegisterRequest;
import com.portafolio.auth.dto.Requests.ResetPasswordRequest;
import com.portafolio.auth.dto.Responses.AuthResponse;
import com.portafolio.auth.dto.Responses.MessageResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.exception.ApiError;
import com.portafolio.auth.exception.ApiException;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.security.RefreshCookies;
import com.portafolio.auth.service.AuthService;
import com.portafolio.auth.service.SessionService.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.net.URI;
import java.time.Clock;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Autenticación", description = "Registro, login, rotación de tokens, logout y recuperación")
@SecurityRequirements // endpoints públicos: no requieren access token
public class AuthController {

    private final AuthService auth;
    private final RefreshCookies cookies;
    private final Clock clock;

    public AuthController(AuthService auth, RefreshCookies cookies, Clock clock) {
        this.auth = auth;
        this.cookies = cookies;
        this.clock = clock;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar cuenta", description = "Crea un usuario con rol USER. 409 si el email ya existe.")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        UserResponse user = auth.register(req, ClientInfo.from(http));
        return ResponseEntity.created(URI.create("/api/users/me")).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un access token JWT (15 min) y fija el refresh token en una cookie HttpOnly. "
                    + "El mismo 401 para email inexistente, contraseña incorrecta o cuenta deshabilitada; "
                    + "429 tras varios intentos fallidos.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return withTokens(auth.login(req, ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar sesión",
            description = "Rota el refresh token de la cookie: el anterior deja de servir. Reutilizar un token ya "
                    + "rotado revoca la sesión completa.")
    public ResponseEntity<?> refresh(HttpServletRequest http) {
        try {
            return withTokens(auth.refresh(readCookie(http), ClientInfo.from(http)));
        } catch (ApiException ex) {
            // Se borra la cookie inválida para que el cliente no siga reintentando con ella.
            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage(), http.getRequestURI()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión y su refresh token. Idempotente.")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        auth.logout(readCookie(http), ClientInfo.from(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Iniciar recuperación",
            description = "Envía (simulado) un enlace de un solo uso. Responde igual exista o no la cuenta.")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req,
                                                          HttpServletRequest http) {
        auth.forgotPassword(req.email(), ClientInfo.from(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(
                "Si el email corresponde a una cuenta, recibirás un enlace para restablecer tu contraseña"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña",
            description = "Cambia la contraseña con un token válido y cierra todas las sesiones del usuario.")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        auth.resetPassword(req, ClientInfo.from(http));
        return new MessageResponse("Contraseña actualizada. Ya puedes iniciar sesión con la nueva contraseña");
    }

    private ResponseEntity<AuthResponse> withTokens(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.create(result.refreshToken(), result.refreshExpiresAt(), clock.instant()).toString())
                .cacheControl(CacheControl.noStore())
                .body(result.response());
    }

    private String readCookie(HttpServletRequest http) {
        Cookie cookie = WebUtils.getCookie(http, cookies.name());
        return cookie == null ? null : cookie.getValue();
    }
}
