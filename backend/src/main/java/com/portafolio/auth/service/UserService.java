package com.portafolio.auth.service;

import com.portafolio.auth.config.AppProperties;
import com.portafolio.auth.dto.Requests.ChangePasswordRequest;
import com.portafolio.auth.dto.Requests.UpdateProfileRequest;
import com.portafolio.auth.dto.Responses.SessionResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.entity.AuditEventType;
import com.portafolio.auth.entity.RevokeReason;
import com.portafolio.auth.entity.Session;
import com.portafolio.auth.entity.User;
import com.portafolio.auth.exception.ApiException;
import com.portafolio.auth.exception.TooManyRequestsException;
import com.portafolio.auth.mapper.AuthMapper;
import com.portafolio.auth.repository.SessionRepository;
import com.portafolio.auth.repository.UserRepository;
import com.portafolio.auth.security.AuthenticatedUser;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.service.mail.MailService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Operaciones del usuario autenticado sobre su propia cuenta. */
@Service
public class UserService {

    private final UserRepository users;
    private final SessionRepository sessionRepository;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final AttemptLimiter limiter;
    private final AuditService audit;
    private final MailService mail;
    private final Clock clock;
    private final AppProperties props;

    public UserService(UserRepository users, SessionRepository sessionRepository, SessionService sessions,
                       PasswordEncoder encoder, AttemptLimiter limiter, AuditService audit, MailService mail,
                       Clock clock, AppProperties props) {
        this.users = users;
        this.sessionRepository = sessionRepository;
        this.sessions = sessions;
        this.encoder = encoder;
        this.limiter = limiter;
        this.audit = audit;
        this.mail = mail;
        this.clock = clock;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthenticatedUser auth) {
        return AuthMapper.toResponse(load(auth.id()));
    }

    @Transactional
    public UserResponse updateProfile(AuthenticatedUser auth, UpdateProfileRequest req, ClientInfo client) {
        User user = load(auth.id());
        user.setName(req.name().strip());
        audit.success(AuditEventType.PROFILE_UPDATE, user.getId(), null, client, "Nombre actualizado");
        return AuthMapper.toResponse(user);
    }

    /** Exige la contraseña actual y cierra las demás sesiones; la actual sigue abierta. */
    @Transactional
    public int changePassword(AuthenticatedUser auth, ChangePasswordRequest req, ClientInfo client) {
        String key = "change-password:" + auth.id();
        long wait = limiter.retryAfterSeconds(key, props.rateLimit().maxLoginFailures());
        if (wait > 0) throw new TooManyRequestsException(wait);

        User user = load(auth.id());
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            limiter.record(key);
            audit.failure(AuditEventType.PASSWORD_CHANGE, user.getId(), null, client, "Contraseña actual incorrecta");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "La contraseña actual no es correcta");
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.businessRule("PASSWORD_UNCHANGED", "La nueva contraseña debe ser distinta de la actual");
        }
        limiter.reset(key);
        user.setPasswordHash(encoder.encode(req.newPassword()));
        int closed = sessions.revokeAll(user.getId(), auth.sessionId(), RevokeReason.PASSWORD_CHANGED);
        audit.success(AuditEventType.PASSWORD_CHANGE, user.getId(), null, client, "Otras sesiones cerradas: " + closed);
        mail.send(user.getEmail(), "Tu contraseña fue cambiada",
                "Hola " + user.getName() + ",\n\nTu contraseña se cambió desde tu perfil. Si no fuiste tú, "
                        + "restablécela de inmediato.", null);
        return closed;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(AuthenticatedUser auth) {
        return sessionRepository.findActiveByUser(auth.id(), clock.instant()).stream()
                .map(s -> AuthMapper.toResponse(s, auth.sessionId()))
                .toList();
    }

    @Transactional
    public void revokeSession(AuthenticatedUser auth, String sessionId, ClientInfo client) {
        Session session = sessionRepository.findByIdAndUserId(sessionId, auth.id())
                .filter(s -> s.isActive(clock.instant()))
                .orElseThrow(() -> ApiException.notFound("Sesión no encontrada"));
        sessions.revoke(session, RevokeReason.REVOKED_BY_USER);
        audit.success(AuditEventType.SESSION_REVOKED, auth.id(), null, client, session.getDeviceLabel());
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
    }
}
