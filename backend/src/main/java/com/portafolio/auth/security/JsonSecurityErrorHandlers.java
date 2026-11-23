package com.portafolio.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portafolio.auth.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Respuestas 401/403 del filtro de seguridad con el mismo formato ApiError que el resto de la API.
 * No se explica por qué falló un token (firma, expiración o sesión) para no dar pistas a un atacante.
 */
@Component
public class JsonSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        boolean badToken = ex instanceof InvalidBearerTokenException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(response, request, HttpStatus.UNAUTHORIZED,
                badToken ? "INVALID_TOKEN" : "UNAUTHORIZED",
                badToken ? "El token no es válido, expiró o la sesión fue cerrada"
                        : "Debes iniciar sesión para acceder a este recurso");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permisos para realizar esta acción");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String code,
                       String message) throws IOException {
        ApiError body = ApiError.of(status, code, message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
