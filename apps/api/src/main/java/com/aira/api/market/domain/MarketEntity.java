package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@jakarta.persistence.Entity
@Table(name = "entity")
public class MarketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 24)
    private EntityType entityType;

    @Column(name = "canonical_name", nullable = false, length = 300)
    private String canonicalName;

    @Column(name = "canonical_key", nullable = false, length = 300)
    private String canonicalKey;

    @Column(name = "market_code", length = 32)
    private String marketCode;

    @Column(length = 64)
    private String symbol;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "marketEntity", fetch = FetchType.LAZY)
    private Set<EventEntity> eventLinks = new HashSet<>();

    protected MarketEntity() {}

    public static MarketEntity company(String canonicalName, String countryCode,
            UUID opaqueIdentity, OffsetDateTime now) {
        if (canonicalName == null || canonicalName.isBlank()
                || canonicalName.trim().length() > 300) {
            throw new IllegalArgumentException("Company canonical name is required");
        }
        if (opaqueIdentity == null || now == null) {
            throw new IllegalArgumentException("Company internal identity and creation time are required");
        }
        String normalizedCountryCode = optionalCountryCode(countryCode);
        MarketEntity entity = new MarketEntity();
        entity.entityType = EntityType.COMPANY;
        entity.canonicalName = canonicalName.trim();
        entity.canonicalKey = "COMPANY:" + opaqueIdentity;
        entity.countryCode = normalizedCountryCode;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static MarketEntity security(String officialName, String marketCode, String symbol,
            UUID opaqueIdentity, OffsetDateTime now) {
        if (officialName == null || officialName.isBlank() || officialName.trim().length() > 300
                || !("KOSPI".equals(marketCode) || "KOSDAQ".equals(marketCode))
                || symbol == null || symbol.isBlank() || symbol.length() > 64
                || opaqueIdentity == null || now == null) {
            throw new IllegalArgumentException("Official security identity metadata is required");
        }
        MarketEntity entity = new MarketEntity();
        entity.entityType = EntityType.SECURITY;
        entity.canonicalName = officialName.trim();
        entity.canonicalKey = "SECURITY:" + opaqueIdentity;
        entity.marketCode = marketCode;
        entity.symbol = symbol;
        entity.countryCode = "KR";
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static MarketEntity market(String canonicalName, String marketCode, String countryCode,
            OffsetDateTime now) {
        if (canonicalName == null || canonicalName.isBlank() || canonicalName.trim().length() > 300
                || marketCode == null || marketCode.isBlank() || marketCode.length() > 32 || now == null) {
            throw new IllegalArgumentException("Market identity metadata is required");
        }
        String normalizedCountryCode = optionalCountryCode(countryCode);
        String normalizedMarketCode = marketCode.trim().toUpperCase(java.util.Locale.ROOT);
        MarketEntity entity = new MarketEntity();
        entity.entityType = EntityType.MARKET;
        entity.canonicalName = canonicalName.trim();
        entity.canonicalKey = "MARKET:" + (normalizedCountryCode == null ? "GLOBAL" : normalizedCountryCode)
                + ":" + normalizedMarketCode;
        entity.marketCode = normalizedMarketCode;
        entity.countryCode = normalizedCountryCode;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    private static String optionalCountryCode(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        String normalized = countryCode.trim();
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "Entity country code must be an ISO alpha-2 uppercase code");
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public EntityType getEntityType() { return entityType; }
    public String getCanonicalName() { return canonicalName; }
    public String getCanonicalKey() { return canonicalKey; }
    public String getMarketCode() { return marketCode; }
    public String getSymbol() { return symbol; }
    public String getCountryCode() { return countryCode; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<EventEntity> getEventLinks() { return eventLinks; }
}
