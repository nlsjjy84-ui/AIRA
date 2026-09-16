package com.aira.api.personalfinance.domain;

import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_finance_transaction")
/**
 * Income/expense observation owned by exactly one AIRA user through its account.
 * Personal transactions stay outside the public canonical Fact/Evidence tables.
 */
public class PersonalFinanceTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PersonalFinanceAccount account;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceTransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "merchant_name", length = 120)
    private String merchantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FinanceTransactionCategory category;

    // Used for import idempotency without retaining the provider's raw transaction identifier.
    @Column(name = "source_ref_hash", nullable = false)
    private byte[] sourceRefHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PersonalFinanceTransaction() {}

    public static PersonalFinanceTransaction create(PersonalFinanceAccount account,
            OffsetDateTime occurredAt, FinanceTransactionDirection direction, BigDecimal amount,
            String currencyCode, String merchantName, FinanceTransactionCategory category,
            byte[] sourceRefHash, OffsetDateTime now) {
        if (account == null || occurredAt == null || direction == null || category == null || now == null) {
            throw new IllegalArgumentException("Transaction identity and time values are required");
        }
        if (amount == null || amount.signum() <= 0 || !validCurrency(currencyCode)) {
            throw new IllegalArgumentException("Positive amount and valid currency are required");
        }
        if (merchantName != null && merchantName.isBlank()) {
            throw new IllegalArgumentException("Merchant name must be null or non-blank");
        }
        if (sourceRefHash == null || sourceRefHash.length == 0) {
            throw new IllegalArgumentException("Hashed source transaction identity is required");
        }
        if (direction == FinanceTransactionDirection.EXPENSE && category == FinanceTransactionCategory.INCOME) {
            throw new IllegalArgumentException("Expense cannot use INCOME category");
        }
        PersonalFinanceTransaction transaction = new PersonalFinanceTransaction();
        transaction.account = account;
        transaction.user = account.getUser();
        transaction.occurredAt = occurredAt;
        transaction.direction = direction;
        transaction.amount = amount;
        transaction.currencyCode = currencyCode;
        transaction.merchantName = merchantName == null ? null : merchantName.trim();
        transaction.category = category;
        transaction.sourceRefHash = sourceRefHash.clone();
        transaction.createdAt = now;
        return transaction;
    }

    private static boolean validCurrency(String value) {
        return value != null && value.matches("[A-Z]{3}");
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public PersonalFinanceAccount getAccount() { return account; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public FinanceTransactionDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getMerchantName() { return merchantName; }
    public FinanceTransactionCategory getCategory() { return category; }
    public byte[] getSourceRefHash() { return sourceRefHash == null ? null : sourceRefHash.clone(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
