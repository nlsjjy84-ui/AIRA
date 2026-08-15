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
