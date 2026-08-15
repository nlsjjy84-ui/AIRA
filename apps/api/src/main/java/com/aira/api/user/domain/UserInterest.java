package com.aira.api.user.domain;

import com.aira.api.market.domain.MarketEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_interest")
public class UserInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private MarketEntity marketEntity;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_level", length = 16)
    private InterestLevel interestLevel;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserInterest() {}

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public MarketEntity getMarketEntity() { return marketEntity; }
    public InterestLevel getInterestLevel() { return interestLevel; }
    public boolean isAlertEnabled() { return alertEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
