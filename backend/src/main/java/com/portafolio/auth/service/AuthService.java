package com.portafolio.auth.service;

import com.portafolio.auth.config.AppProperties;
import com.portafolio.auth.dto.Requests.LoginRequest;
import com.portafolio.auth.dto.Requests.RegisterRequest;
import com.portafolio.auth.dto.Requests.ResetPasswordRequest;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.entity.*;
import com.portafolio.auth.exception.ApiException;
import com.portafolio.auth.exception.TooManyRequestsException;
import com.portafolio.auth.mapper.AuthMapper;
import com.portafolio.auth.repository.PasswordResetTokenRepository;
import com.portafolio.auth.repository.RefreshTokenRepository;
import com.portafolio.auth.repository.RoleRepository;
import com.portafolio.auth.repository.UserRepository;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.security.SecureTokens;
import com.portafolio.auth.service.SessionService.LoginResult;
import com.portafolio.auth.service.mail.MailService;
import com.portafolio.auth.validation.EmailNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    /** Si un refresh token rotado se reutiliza dentro de este margen se asume una carrera benigna (dos pestañas). */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final SecureTokens tokens;
    private final AttemptLimiter limiter;
    private final AuditService audit;
    private final MailService mail;
    private final Clock clock;
    private final AppProperties props;
    /** Hash de referencia para igualar el tiempo de respuesta cuando el email no existe. */
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, SessionService sessions, PasswordEncoder encoder,
                       SecureTokens tokens, AttemptLimiter limiter, AuditService audit, MailService mail,
                       Clock clock, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.audit = audit;
        this.mail = mail;
        this.clock = clock;
        this.props = props;
        this.dummyHash = encoder.encode(tokens.generate());
    }

    // ---------------------------------------------------------------- registro

    @Transactional
    public UserResponse register(RegisterRequest req, ClientInfo client) {
        String email = EmailNormalizer.normalize(req.email());
        if (users.existsByEmail(email)) {
            audit.failure(AuditEventType.REGISTER, null, email, client, "Email ya registrado");
            throw ApiException.conflict("EMAIL_IN_USE", "Ya existe una cuenta registrada con ese email");
        }
        User user = new User(req.name().strip(), email, encoder.encode(req.password()));
        user.getRoles().add(roles.findByName(RoleName.USER).orElseThrow());
        users.saveAndFlush(user); // la restricción única de la base cubre registros simultáneos
        audit.success(AuditEventType.REGISTER, user.getId(), email, client, null);
        return AuthMapper.toResponse(user);
    }

    // ---------------------------------------------------------------- login

    @Transactional
    public LoginResult login(LoginRequest req, ClientInfo client) {
        String email = EmailNormalizer.normalize(req.email());
        String emailKey = "login:" + email;
        String ipKey = "login-ip:" + client.ipAddress();
        int max = props.rateLimit().maxLoginFailures();

        long wait = Math.max(limiter.retryAfterSeconds(emailKey, max), limiter.retryAfterSeconds(ipKey, max * 4));
        if (wait > 0) {
            audit.failure(AuditEventType.LOGIN_LOCKED, null, email, client, "Bloqueo temporal por intentos fallidos");
            throw new TooManyRequestsException(wait);
        }

        Optional<User> found = users.findByEmail(email);
        // Siempre se ejecuta BCrypt, exista o no la cuenta: el tiempo de respuesta no revela emails registrados.
        boolean passwordOk = encoder.matches(req.password(), found.map(User::getPasswordHash).orElse(dummyHash));

        if (found.isEmpty() || !passwordOk || !found.get().isEnabled()) {
            limiter.record(emailKey);
            limiter.record(ipKey);
            String reason = found.isEmpty() ? "Cuenta inexistente"
                    : !passwordOk ? "Contraseña incorrecta" : "Cuenta deshabilitada";
            audit.failure(AuditEventType.LOGIN, found.map(User::getId).orElse(null), email, client, reason);
            throw ApiException.invalidCredentials();
        }

        limiter.reset(emailKey);
        User user = found.get();
        LoginResult result = sessions.start(user, client);
        audit.success(AuditEventType.LOGIN, user.getId(), email, client, client.deviceLabel());
        return result;
    }

    // ---------------------------------------------------------------- refresh (rotación)

    /**
     * Rota el refresh token: el recibido queda usado y se emite uno nuevo. Si llega un token ya rotado
     * (fuera del margen de gracia) se interpreta como robo y se revoca la sesión completa.
     * {@code noRollbackFor}: esa revocación debe persistir aunque la respuesta sea un 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) throw ApiException.invalidRefreshToken();
        RefreshToken token = refreshTokens.findByHashForUpdate(tokens.hash(rawToken))
                .orElseThrow(ApiException::invalidRefreshToken);
        Instant now = clock.instant();
        Session session = token.getSession();
        User user = session.getUser();

        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            boolean benignRace = token.getRevokedAt() == null && token.getUsedAt().plus(REUSE_GRACE).isAfter(now);
            if (!benignRace && session.isActive(now)) {
                sessions.revoke(session, RevokeReason.TOKEN_REUSE);
                audit.failure(AuditEventType.REFRESH_TOKEN_REUSE, user.getId(), null, client,
                        "Refresh token reutilizado: sesión revocada por seguridad");
            }
            throw ApiException.invalidRefreshToken();
        }
        if (!token.isUsable(now) || !session.isActive(now) || !user.isEnabled()) {
            throw ApiException.invalidRefreshToken();
        }

        token.markUsed(now);
        session.touch(now);
        return sessions.issueTokens(user, session, now);
    }

    // ---------------------------------------------------------------- logout

    /** Idempotente: un token desconocido o ya revocado no produce error. */
    @Transactional
    public void logout(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByHash(tokens.hash(rawToken)).ifPresent(token -> {
            Session session = token.getSession();
            if (session.getRevokedAt() == null) {
                sessions.revoke(session, RevokeReason.LOGOUT);
                audit.success(AuditEventType.LOGOUT, session.getUser().getId(), null, client, null);
            }
        });
    }

    // ---------------------------------------------------------------- recuperación de contraseña

    /**
     * Siempre termina igual para el cliente (202 con el mismo mensaje), exista o no la cuenta.
     * Solo el último enlace enviado es válido.
     */
    @Transactional
    public void forgotPassword(String rawEmail, ClientInfo client) {
        String email = EmailNormalizer.normalize(rawEmail);
        String key = "reset:" + email;
        if (limiter.retryAfterSeconds(key, props.rateLimit().maxResetRequests()) > 0) {
            audit.failure(AuditEventType.PASSWORD_RESET_REQUEST, null, email, client, "Límite de solicitudes alcanzado");
            return;
        }
        limiter.record(key);

        Optional<User> found = users.findByEmail(email).filter(User::isEnabled);
        if (found.isEmpty()) {
            audit.failure(AuditEventType.PASSWORD_RESET_REQUEST, null, email, client, "Cuenta inexistente o deshabilitada");
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        Duration ttl = Duration.ofMinutes(props.passwordReset().ttlMinutes());
        resetTokens.invalidatePendingForUser(user.getId(), now);
        String rawToken = tokens.generate();
        resetTokens.save(new PasswordResetToken(user, tokens.hash(rawToken), now, now.plus(ttl)));

        String link = props.passwordReset().frontendUrl() + "/reset-password?token=" + rawToken;
        mail.send(user.getEmail(), "Restablece tu contraseña",
                "Hola " + user.getName() + ",\n\nRecibimos una solicitud para restablecer tu contraseña. "
                        + "El enlace es válido durante " + ttl.toMinutes() + " minutos y solo puede usarse una vez.\n\n"
                        + "Si no fuiste tú, ignora este correo: tu contraseña no cambiará.",
                link);
        audit.success(AuditEventType.PASSWORD_RESET_REQUEST, user.getId(), email, client, null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req, ClientInfo client) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByHashForUpdate(tokens.hash(req.token())).orElse(null);
        if (token == null || !token.isUsable(now) || !token.getUser().isEnabled()) {
            audit.failure(AuditEventType.PASSWORD_RESET, token == null ? null : token.getUser().getId(), null, client,
                    token == null ? "Token inexistente" : token.getUsedAt() != null ? "Token ya usado" : "Token expirado");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                    "El enlace de recuperación no es válido, ya se usó o expiró. Solicita uno nuevo");
        }

        User user = token.getUser();
        user.setPasswordHash(encoder.encode(req.newPassword()));
        token.markUsed(now);
        resetTokens.invalidatePendingForUser(user.getId(), now);
        // Quien tuviera una sesión abierta con la contraseña anterior pierde el acceso.
        sessions.revokeAll(user.getId(), null, RevokeReason.PASSWORD_RESET);
        limiter.reset("login:" + user.getEmail());

        audit.success(AuditEventType.PASSWORD_RESET, user.getId(), user.getEmail(), client, "Sesiones cerradas");
        mail.send(user.getEmail(), "Tu contraseña fue cambiada",
                "Hola " + user.getName() + ",\n\nTu contraseña se restableció correctamente y se cerraron todas "
                        + "tus sesiones. Si no fuiste tú, contacta al administrador.", null);
    }
}
