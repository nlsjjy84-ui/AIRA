package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.UUID;

@Entity
@Table(name = "user_session")
public class UserSession {
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    private AuthenticationCredential credential;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rotated_from_session_id")
    private UserSession rotatedFromSession;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private OffsetDateTime idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private OffsetDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    private SessionRevokeReason revokeReason;

    protected UserSession() {}

    public static UserSession create(
            AppUser user, AuthenticationCredential credential, byte[] tokenHash, OffsetDateTime now) {
        if (user == null || credential == null || tokenHash == null || tokenHash.length == 0 || now == null) {
            throw new IllegalArgumentException("Valid session creation values are required");
        }
        UserSession session = new UserSession();
        session.user = user;
        session.credential = credential;
        session.tokenHash = tokenHash.clone();
        session.issuedAt = now;
        session.lastSeenAt = now;
        session.absoluteExpiresAt = now.plus(ABSOLUTE_TIMEOUT);
        session.idleExpiresAt = now.plus(IDLE_TIMEOUT);
        return session;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public AuthenticationCredential getCredential() { return credential; }
    public byte[] getTokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
    public UserSession getRotatedFromSession() { return rotatedFromSession; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public OffsetDateTime getIdleExpiresAt() { return idleExpiresAt; }
    public OffsetDateTime getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public SessionRevokeReason getRevokeReason() { return revokeReason; }
}
