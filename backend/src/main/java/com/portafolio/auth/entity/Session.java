package com.portafolio.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Sesión iniciada en un dispositivo. Los access tokens llevan su id (claim {@code sid}) y se rechazan
 * en cuanto la sesión se revoca (logout, reutilización de refresh token, cambio de contraseña, bloqueo).
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 40)
    private RevokeReason revokeReason;

    protected Session() {}

    public Session(User user, String deviceLabel, String ipAddress, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.deviceLabel = deviceLabel;
        this.ipAddress = ipAddress;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, RevokeReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public void touch(Instant now) { lastUsedAt = now; }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getDeviceLabel() { return deviceLabel; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public RevokeReason getRevokeReason() { return revokeReason; }
}
