package com.aira.api.delivery.domain;

import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "briefing")
public class Briefing {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "briefing_type", nullable = false, length = 24)
    private String briefingType;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BriefingStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "dedup_key", nullable = false)
    private byte[] dedupKey;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "briefing", fetch = FetchType.LAZY)
    private Set<BriefingItem> items = new HashSet<>();

    protected Briefing() {}

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public String getBriefingType() { return briefingType; }
    public OffsetDateTime getPeriodStart() { return periodStart; }
    public OffsetDateTime getPeriodEnd() { return periodEnd; }
    public String getPolicyVersion() { return policyVersion; }
    public BriefingStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public byte[] getDedupKey() { return dedupKey == null ? null : dedupKey.clone(); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<BriefingItem> getItems() { return items; }
}
