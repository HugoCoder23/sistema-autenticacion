package com.portafolio.auth.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementación por defecto cuando la bandeja de desarrollo está desactivada. Punto de extensión para un
 * proveedor real (SMTP, SES, SendGrid...). Nunca registra el cuerpo ni el enlace del correo.
 */
@Service
@ConditionalOnProperty(name = "app.mail.dev-outbox", havingValue = "false", matchIfMissing = true)
public class LoggingMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Override
    public void send(String to, String subject, String body, String actionUrl) {
        log.warn("No hay proveedor de correo configurado; no se envió '{}' a {}", subject, mask(to));
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
