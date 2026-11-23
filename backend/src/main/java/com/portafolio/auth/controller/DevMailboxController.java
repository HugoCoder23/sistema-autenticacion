package com.portafolio.auth.controller;

import com.portafolio.auth.service.mail.DevOutboxMailService;
import com.portafolio.auth.service.mail.MailMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SOLO DESARROLLO/DEMO: muestra los correos simulados (incluidos los enlaces de recuperación). El bean no
 * existe si {@code app.mail.dev-outbox=false}, que es el valor por defecto en el perfil prod.
 */
@RestController
@RequestMapping("/api/dev/mailbox")
@ConditionalOnProperty(name = "app.mail.dev-outbox", havingValue = "true")
@Tag(name = "9. Bandeja simulada (solo desarrollo)")
@SecurityRequirements
public class DevMailboxController {

    private final DevOutboxMailService outbox;

    public DevMailboxController(DevOutboxMailService outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    @Operation(summary = "Ver correos simulados", description = "Opcionalmente filtra por destinatario (to).")
    public List<MailMessage> messages(@RequestParam(required = false) String to) {
        return outbox.messages().stream()
                .filter(m -> to == null || m.to().equalsIgnoreCase(to.strip()))
                .toList();
    }

    @DeleteMapping
    @Operation(summary = "Vaciar la bandeja")
    public ResponseEntity<Void> clear() {
        outbox.clear();
        return ResponseEntity.noContent().build();
    }
}
