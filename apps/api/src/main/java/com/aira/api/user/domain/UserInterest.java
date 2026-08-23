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

    public static UserInterest create(AppUser user, MarketEntity marketEntity, OffsetDateTime now) {
        if (user == null || marketEntity == null || now == null) {
            throw new IllegalArgumentException("User interest creation values are required");
        }
        UserInterest interest = new UserInterest();
        interest.user = user;
        interest.marketEntity = marketEntity;
        interest.interestLevel = null;
        interest.alertEnabled = false;
        interest.createdAt = now;
        interest.updatedAt = now;
        return interest;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public MarketEntity getMarketEntity() { return marketEntity; }
    public InterestLevel getInterestLevel() { return interestLevel; }
    public boolean isAlertEnabled() { return alertEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setAlertEnabled(boolean enabled, OffsetDateTime now) {
        if (now == null) throw new IllegalArgumentException("Alert setting time is required");
        alertEnabled = enabled;
        updatedAt = now;
    }
}
