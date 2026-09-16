package com.aira.api.personalfinance.domain;

import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_finance_connection")
/**
 * One user-owned finance-data connection.
 *
 * <p>This is deliberately separate from AIRA's public market Fact/Evidence model.
 * DEMO_IMPORT and a future real MYDATA_API connection share the same boundary,
 * but demo data must never be presented as a live financial-institution link.</p>
 */
public class PersonalFinanceConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private FinanceConnectionSource sourceType;

    @Column(name = "provider_key", nullable = false, length = 64)
    private String providerKey;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceConnectionStatus status;

    @Column(name = "consented_at", nullable = false)
    private OffsetDateTime consentedAt;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "disconnected_at")
    private OffsetDateTime disconnectedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PersonalFinanceConnection() {}

    public static PersonalFinanceConnection create(AppUser user, FinanceConnectionSource sourceType,
            String providerKey, String displayName, OffsetDateTime consentedAt) {
        if (user == null || sourceType == null || consentedAt == null) {
            throw new IllegalArgumentException("Connection owner, source and consent time are required");
        }
        if (providerKey == null || providerKey.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Connection provider and display name are required");
        }
        PersonalFinanceConnection connection = new PersonalFinanceConnection();
        connection.user = user;
        connection.sourceType = sourceType;
        connection.providerKey = providerKey.trim();
        connection.displayName = displayName.trim();
        connection.status = FinanceConnectionStatus.ACTIVE;
        connection.consentedAt = consentedAt;
        connection.createdAt = consentedAt;
        connection.updatedAt = consentedAt;
        return connection;
    }

    public void markSynced(OffsetDateTime at) {
        if (at == null || at.isBefore(consentedAt)) throw new IllegalArgumentException("Invalid sync time");
        lastSyncedAt = at;
        updatedAt = at;
    }

    /** Marks the connection unusable from this point forward; callers must fail closed. */
    public void disconnect(OffsetDateTime at) {
        if (at == null || at.isBefore(consentedAt)) throw new IllegalArgumentException("Invalid disconnect time");
        status = FinanceConnectionStatus.DISCONNECTED;
        disconnectedAt = at;
        updatedAt = at;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public FinanceConnectionSource getSourceType() { return sourceType; }
    public String getProviderKey() { return providerKey; }
    public String getDisplayName() { return displayName; }
    public FinanceConnectionStatus getStatus() { return status; }
    public OffsetDateTime getConsentedAt() { return consentedAt; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public OffsetDateTime getDisconnectedAt() { return disconnectedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
