package com.portafolio.auth.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Bandeja simulada para desarrollo y demo: guarda los últimos correos en memoria y los expone en
 * {@code GET /api/dev/mailbox}. El log solo registra destinatario y asunto, nunca el enlace con el token.
 * Se desactiva con DEV_OUTBOX_ENABLED=false (por defecto en el perfil prod).
 */
@Service
@ConditionalOnProperty(name = "app.mail.dev-outbox", havingValue = "true")
public class DevOutboxMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(DevOutboxMailService.class);
    private static final int CAPACITY = 50;

    private final Deque<MailMessage> outbox = new ArrayDeque<>();
    private final Clock clock;

    public DevOutboxMailService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void send(String to, String subject, String body, String actionUrl) {
        MailMessage message = new MailMessage(UUID.randomUUID().toString(), to, subject, body, actionUrl, clock.instant());
        synchronized (outbox) {
            outbox.addFirst(message);
            while (outbox.size() > CAPACITY) outbox.removeLast();
        }
        log.info("Correo simulado guardado en la bandeja de desarrollo: {}", message);
    }

    public List<MailMessage> messages() {
        synchronized (outbox) {
            return List.copyOf(outbox);
        }
    }

    public void clear() {
        synchronized (outbox) {
            outbox.clear();
        }
    }
}
