package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "authentication_credential")
public class AuthenticationCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private OffsetDateTime passwordChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CredentialStatus status;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "credential", fetch = FetchType.LAZY)
    private Set<UserSession> sessions = new HashSet<>();

    protected AuthenticationCredential() {}

    public static AuthenticationCredential create(
            AppUser user, String passwordHash, OffsetDateTime now) {
        if (user == null || passwordHash == null || !passwordHash.startsWith("$argon2id$") || now == null) {
            throw new IllegalArgumentException("Valid credential creation values are required");
        }
        AuthenticationCredential credential = new AuthenticationCredential();
        credential.user = user;
        credential.passwordHash = passwordHash;
        credential.passwordChangedAt = now;
        credential.status = CredentialStatus.ACTIVE;
        credential.failedAttempts = 0;
        credential.createdAt = now;
        credential.updatedAt = now;
        return credential;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getPasswordChangedAt() { return passwordChangedAt; }
    public CredentialStatus getStatus() { return status; }
    public int getFailedAttempts() { return failedAttempts; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<UserSession> getSessions() { return sessions; }
}
