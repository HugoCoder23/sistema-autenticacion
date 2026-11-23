package com.portafolio.auth.dto;

import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/** Cuerpos de respuesta. Ninguno contiene passwordHash, refresh tokens ni tokens de recuperación. */
public final class Responses {

    private Responses() {}

    public record UserResponse(Long id, String name, String email, List<String> roles, boolean enabled,
                               Instant createdAt) {}

    /** El refresh token NO va aquí: viaja solo en la cookie HttpOnly. */
    public record AuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
                               UserResponse user) {}

    public record SessionResponse(String id, String deviceLabel, String ipAddress, Instant createdAt,
                                  Instant lastUsedAt, Instant expiresAt, boolean current) {}

    public record AuditEventResponse(Long id, String type, String outcome, Long userId, Long actorId,
                                     String email, String ipAddress, String userAgent, String detail,
                                     Instant createdAt) {}

    public record StatsResponse(long totalUsers, long enabledUsers, long disabledUsers, long admins,
                                long activeSessions, long failedLogins24h) {}

    public record MessageResponse(String message) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
