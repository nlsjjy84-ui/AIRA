package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recovery_email_verification")
public class RecoveryEmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "candidate_email_ciphertext", nullable = false)
    private byte[] candidateEmailCiphertext;

    @Column(name = "candidate_email_lookup_hash", nullable = false)
    private byte[] candidateEmailLookupHash;

    @Column(name = "encryption_key_version", nullable = false)
    private short encryptionKeyVersion;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;

    protected RecoveryEmailVerification() {}

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public byte[] getCandidateEmailCiphertext() { return copy(candidateEmailCiphertext); }
    public byte[] getCandidateEmailLookupHash() { return copy(candidateEmailLookupHash); }
    public short getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public byte[] getTokenHash() { return copy(tokenHash); }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getInvalidatedAt() { return invalidatedAt; }

    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
