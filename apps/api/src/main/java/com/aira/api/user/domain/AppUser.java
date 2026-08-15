package com.aira.api.user.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20, updatable = false)
    private String nickname;

    @Column(name = "nickname_normalized", nullable = false, length = 20, updatable = false)
    private String nicknameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(length = 16)
    private String locale;

    @Column(length = 64)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private AuthenticationCredential credential;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<RecoveryEmail> recoveryEmails = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<RecoveryEmailVerification> recoveryEmailVerifications = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserSession> sessions = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserInterest> interests = new HashSet<>();

    protected AppUser() {}

    public static AppUser create(String nickname, String nicknameNormalized, OffsetDateTime now) {
        if (nickname == null || nicknameNormalized == null || now == null) {
            throw new IllegalArgumentException("User creation values are required");
        }
        AppUser user = new AppUser();
        user.nickname = nickname;
        user.nicknameNormalized = nicknameNormalized;
        user.status = UserStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public UUID getId() { return id; }
    public String getNickname() { return nickname; }
    public String getNicknameNormalized() { return nicknameNormalized; }
    public UserStatus getStatus() { return status; }
    public String getLocale() { return locale; }
    public String getTimezone() { return timezone; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public AuthenticationCredential getCredential() { return credential; }
    public Set<RecoveryEmail> getRecoveryEmails() { return recoveryEmails; }
    public Set<RecoveryEmailVerification> getRecoveryEmailVerifications() { return recoveryEmailVerifications; }
    public Set<UserSession> getSessions() { return sessions; }
    public Set<UserInterest> getInterests() { return interests; }
}
