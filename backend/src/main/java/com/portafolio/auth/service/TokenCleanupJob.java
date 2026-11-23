package com.portafolio.auth.service;

import com.portafolio.auth.repository.PasswordResetTokenRepository;
import com.portafolio.auth.repository.RefreshTokenRepository;
import com.portafolio.auth.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Limpieza periódica de tokens y sesiones vencidos, y de contadores de intentos caducados. */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionRepository sessions;
    private final AttemptLimiter limiter;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                           SessionRepository sessions, AttemptLimiter limiter, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 17 * * * *}")
    @Transactional
    public void cleanup() {
        Instant now = clock.instant();
        int refresh = refreshTokens.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        int reset = resetTokens.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        // Las sesiones cerradas se conservan 30 días para que el historial de auditoría tenga contexto.
        int old = sessions.deleteOlderThan(now.minus(Duration.ofDays(30)));
        limiter.purgeExpired();
        if (refresh + reset + old > 0) {
            log.info("Limpieza: {} refresh tokens, {} tokens de recuperación, {} sesiones", refresh, reset, old);
        }
    }
}
