package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "recovery_email")
public class RecoveryEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecoveryEmailPurpose purpose;

    @Column(name = "email_ciphertext", nullable = false)
    private byte[] emailCiphertext;

    @Column(name = "email_lookup_hash", nullable = false)
    private byte[] emailLookupHash;

    @Column(name = "encryption_key_version", nullable = false)
    private short encryptionKeyVersion;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "recoveryEmail", fetch = FetchType.LAZY)
    private Set<PasswordResetToken> passwordResetTokens = new HashSet<>();

    protected RecoveryEmail() {}

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public RecoveryEmailPurpose getPurpose() { return purpose; }
    public byte[] getEmailCiphertext() { return copy(emailCiphertext); }
    public byte[] getEmailLookupHash() { return copy(emailLookupHash); }
    public short getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Set<PasswordResetToken> getPasswordResetTokens() { return passwordResetTokens; }

    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
