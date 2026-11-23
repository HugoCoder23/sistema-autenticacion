package com.portafolio.auth.service.mail;

import java.time.Instant;

/**
 * Correo saliente. {@code actionUrl} es el enlace principal (p. ej. restablecer contraseña).
 * {@link #toString()} no lo incluye para que nunca termine en un log.
 */
public record MailMessage(String id, String to, String subject, String body, String actionUrl, Instant sentAt) {

    @Override
    public String toString() {
        return "MailMessage[to=" + to + ", subject=" + subject + "]";
    }
}
