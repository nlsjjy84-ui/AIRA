package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "statistical_series")
public class StatisticalSeries {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_entity_id", nullable = false)
    private MarketEntity subjectEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatisticalMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatisticalFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatisticalAdjustment adjustment;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_kind", nullable = false, length = 16)
    private StatisticalValueKind valueKind;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StatisticalSeries() {}

    public UUID getId() { return id; }
    public MarketEntity getSubjectEntity() { return subjectEntity; }
    public StatisticalMetric getMetric() { return metric; }
    public StatisticalFrequency getFrequency() { return frequency; }
    public StatisticalAdjustment getAdjustment() { return adjustment; }
    public StatisticalValueKind getValueKind() { return valueKind; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
