package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_email_id", nullable = false)
    private RecoveryEmail recoveryEmail;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;

    @Column(name = "invalid_reason", length = 32)
    private String invalidReason;

    protected PasswordResetToken() {}

    public UUID getId() { return id; }
    public RecoveryEmail getRecoveryEmail() { return recoveryEmail; }
    public byte[] getTokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getUsedAt() { return usedAt; }
    public OffsetDateTime getInvalidatedAt() { return invalidatedAt; }
    public String getInvalidReason() { return invalidReason; }
}
