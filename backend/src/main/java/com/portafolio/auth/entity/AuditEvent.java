package com.portafolio.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Registro de auditoría. Por diseño no tiene campos para contraseñas, hashes ni tokens. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    /** Usuario afectado por el evento. */
    @Column(name = "user_id")
    private Long userId;

    /** Quién ejecutó la acción cuando no es el propio usuario (p. ej. un ADMIN). */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 160)
    private String email;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    @Column(length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(AuditEventType type, AuditOutcome outcome, Long userId, Long actorId, String email,
                      String ipAddress, String userAgent, String detail, Instant createdAt) {
        this.type = type;
        this.outcome = outcome;
        this.userId = userId;
        this.actorId = actorId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public AuditEventType getType() { return type; }
    public AuditOutcome getOutcome() { return outcome; }
    public Long getUserId() { return userId; }
    public Long getActorId() { return actorId; }
    public String getEmail() { return email; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
