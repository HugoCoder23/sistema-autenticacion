package com.portafolio.auth.service;

import com.portafolio.auth.entity.AuditEvent;
import com.portafolio.auth.entity.AuditEventType;
import com.portafolio.auth.entity.AuditOutcome;
import com.portafolio.auth.repository.AuditEventRepository;
import com.portafolio.auth.security.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Auditoría de inicios de sesión y cambios sensibles. Se escribe en una transacción propia para que los
 * intentos fallidos queden registrados aunque la operación principal se revierta. La firma del método no
 * acepta contraseñas ni tokens: solo identificadores y un detalle legible.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW) // a nivel de clase: aplica también a success()/failure()
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void record(AuditEventType type, AuditOutcome outcome, Long userId, Long actorId, String email,
                       ClientInfo client, String detail) {
        events.save(new AuditEvent(type, outcome, userId, actorId, email,
                client == null ? null : client.ipAddress(),
                client == null ? null : client.userAgent(),
                detail, clock.instant()));
        log.info("audit type={} outcome={} userId={} actorId={}", type, outcome, userId, actorId);
    }

    public void success(AuditEventType type, Long userId, String email, ClientInfo client, String detail) {
        record(type, AuditOutcome.SUCCESS, userId, null, email, client, detail);
    }

    public void failure(AuditEventType type, Long userId, String email, ClientInfo client, String detail) {
        record(type, AuditOutcome.FAILURE, userId, null, email, client, detail);
    }
}
