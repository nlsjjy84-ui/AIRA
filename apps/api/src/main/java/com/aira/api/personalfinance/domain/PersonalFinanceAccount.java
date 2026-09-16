package com.aira.api.personalfinance.domain;

import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_finance_account")
/**
 * User-owned account/card display unit imported through one finance connection.
 * Raw account/card numbers are intentionally not stored by this entity.
 */
public class PersonalFinanceAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private PersonalFinanceConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private FinanceAccountType accountType;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // Stable source identity only; never replace this with a raw account/card number.
    @Column(name = "source_ref_hash", nullable = false)
    private byte[] sourceRefHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PersonalFinanceAccount() {}

    public static PersonalFinanceAccount create(PersonalFinanceConnection connection,
            FinanceAccountType accountType, String displayName, String currencyCode,
            byte[] sourceRefHash, OffsetDateTime now) {
        if (connection == null || accountType == null || now == null) {
            throw new IllegalArgumentException("Account connection, type and time are required");
        }
        if (displayName == null || displayName.isBlank() || !validCurrency(currencyCode)) {
            throw new IllegalArgumentException("Valid account display name and currency are required");
        }
        if (sourceRefHash == null || sourceRefHash.length == 0) {
            throw new IllegalArgumentException("Hashed source account identity is required");
        }
        PersonalFinanceAccount account = new PersonalFinanceAccount();
        account.connection = connection;
        account.user = connection.getUser();
        account.accountType = accountType;
        account.displayName = displayName.trim();
        account.currencyCode = currencyCode;
        account.sourceRefHash = sourceRefHash.clone();
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    private static boolean validCurrency(String value) {
        return value != null && value.matches("[A-Z]{3}");
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public PersonalFinanceConnection getConnection() { return connection; }
    public FinanceAccountType getAccountType() { return accountType; }
    public String getDisplayName() { return displayName; }
    public String getCurrencyCode() { return currencyCode; }
    public byte[] getSourceRefHash() { return sourceRefHash == null ? null : sourceRefHash.clone(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
