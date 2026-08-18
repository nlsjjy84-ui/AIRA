package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_authority_scope")
public class SourceAuthorityScope {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 48)
    private AuthorityScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority_role", nullable = false, length = 32)
    private AuthorityRole authorityRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_entity_id")
    private MarketEntity subjectEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurisdiction_entity_id")
    private MarketEntity jurisdictionEntity;

    @Column(name = "basis_url", nullable = false, columnDefinition = "text")
    private String basisUrl;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SourceAuthorityScope() {}

    @PrePersist
    private void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Source getSource() { return source; }
    public AuthorityScopeType getScopeType() { return scopeType; }
    public AuthorityRole getAuthorityRole() { return authorityRole; }
    public MarketEntity getSubjectEntity() { return subjectEntity; }
    public MarketEntity getJurisdictionEntity() { return jurisdictionEntity; }
    public String getBasisUrl() { return basisUrl; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
