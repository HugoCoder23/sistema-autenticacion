package com.portafolio.auth.service;

import com.portafolio.auth.config.AppProperties;
import com.portafolio.auth.dto.Responses.AuthResponse;
import com.portafolio.auth.entity.RefreshToken;
import com.portafolio.auth.entity.RevokeReason;
import com.portafolio.auth.entity.Session;
import com.portafolio.auth.entity.User;
import com.portafolio.auth.mapper.AuthMapper;
import com.portafolio.auth.repository.RefreshTokenRepository;
import com.portafolio.auth.repository.SessionRepository;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.security.JwtService;
import com.portafolio.auth.security.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Ciclo de vida de sesiones: creación, emisión de tokens y revocación. Siempre dentro de una transacción. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SessionService {

    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final SecureTokens tokens;
    private final JwtService jwt;
    private final Clock clock;
    private final Duration sessionTtl;

    public SessionService(SessionRepository sessions, RefreshTokenRepository refreshTokens, SecureTokens tokens,
                          JwtService jwt, Clock clock, AppProperties props) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.jwt = jwt;
        this.clock = clock;
        this.sessionTtl = Duration.ofDays(props.refresh().ttlDays());
    }

    /**
     * Abre una sesión con vida máxima absoluta (REFRESH_TTL_DAYS): las rotaciones no la extienden, así que
     * pasado ese plazo hay que volver a iniciar sesión.
     */
    public LoginResult start(User user, ClientInfo client) {
        Instant now = clock.instant();
        Session session = sessions.save(new Session(user, client.deviceLabel(), client.ipAddress(), now,
                now.plus(sessionTtl)));
        return issueTokens(user, session, now);
    }

    /** Emite un access token y un refresh token nuevo para una sesión existente. */
    public LoginResult issueTokens(User user, Session session, Instant now) {
        String rawRefresh = tokens.generate();
        refreshTokens.save(new RefreshToken(session, tokens.hash(rawRefresh), now, session.getExpiresAt()));
        JwtService.IssuedToken access = jwt.issue(user, session);
        AuthResponse body = new AuthResponse(access.value(), "Bearer", access.expiresInSeconds(), access.expiresAt(),
                AuthMapper.toResponse(user));
        return new LoginResult(body, rawRefresh, session.getExpiresAt());
    }

    public void revoke(Session session, RevokeReason reason) {
        Instant now = clock.instant();
        session.revoke(now, reason);
        refreshTokens.revokeAllForSession(session.getId(), now);
    }

    /** Revoca todas las sesiones del usuario salvo {@code keepSessionId} (null = todas). */
    public int revokeAll(Long userId, String keepSessionId, RevokeReason reason) {
        Instant now = clock.instant();
        refreshTokens.revokeAllForUser(userId, keepSessionId, now);
        return sessions.revokeAllForUser(userId, keepSessionId, now, reason);
    }

    /** Resultado de login/refresh: cuerpo JSON + refresh token en claro (solo para la cookie). */
    public record LoginResult(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {

        @Override
        public String toString() { return "LoginResult[***]"; }
    }
}
