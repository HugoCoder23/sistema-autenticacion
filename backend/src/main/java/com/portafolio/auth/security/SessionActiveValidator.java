package com.portafolio.auth.security;

import com.portafolio.auth.repository.SessionRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Un JWT válido por firma y fecha no basta: su sesión ({@code sid}) debe seguir activa y el usuario
 * habilitado. Así el logout, la revocación de sesiones o el bloqueo de un usuario surten efecto de
 * inmediato y no al expirar el access token. Coste: una consulta por clave primaria por petición.
 */
@Component
public class SessionActiveValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "La sesión del token no está activa", null));

    private final SessionRepository sessions;
    private final Clock clock;

    public SessionActiveValidator(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sid = jwt.getClaimAsString(JwtService.CLAIM_SESSION_ID);
        Long userId = parseUserId(jwt.getSubject());
        if (sid == null || userId == null) return INVALID;
        return sessions.isActive(sid, userId, clock.instant()) ? OAuth2TokenValidatorResult.success() : INVALID;
    }

    private static Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
