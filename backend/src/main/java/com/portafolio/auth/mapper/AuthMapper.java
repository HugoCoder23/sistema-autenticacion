package com.portafolio.auth.mapper;

import com.portafolio.auth.dto.Responses.AuditEventResponse;
import com.portafolio.auth.dto.Responses.SessionResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.entity.AuditEvent;
import com.portafolio.auth.entity.Session;
import com.portafolio.auth.entity.User;

/** Entidad -> DTO. Es el único punto donde se decide qué campos salen de la API. */
public final class AuthMapper {

    private AuthMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.roleNames(), user.isEnabled(),
                user.getCreatedAt());
    }

    public static SessionResponse toResponse(Session session, String currentSessionId) {
        return new SessionResponse(session.getId(), session.getDeviceLabel(), session.getIpAddress(),
                session.getCreatedAt(), session.getLastUsedAt(), session.getExpiresAt(),
                session.getId().equals(currentSessionId));
    }

    public static AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getType().name(), e.getOutcome().name(), e.getUserId(),
                e.getActorId(), e.getEmail(), e.getIpAddress(), e.getUserAgent(), e.getDetail(), e.getCreatedAt());
    }
}
