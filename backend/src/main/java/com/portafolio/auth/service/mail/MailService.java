package com.portafolio.auth.service.mail;

/** Envío de correos. En desarrollo se simula; en producción se conectaría un proveedor SMTP/API. */
public interface MailService {

    void send(String to, String subject, String body, String actionUrl);
}
