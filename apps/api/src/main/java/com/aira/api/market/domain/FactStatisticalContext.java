package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fact_statistical_context")
public class FactStatisticalContext {
    @Id
    @Column(name = "fact_id")
    private UUID factId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fact_id", nullable = false)
    private Fact fact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statistical_series_id", nullable = false)
    private StatisticalSeries statisticalSeries;

    @Enumerated(EnumType.STRING)
    @Column(name = "canonical_unit", nullable = false, length = 32)
    private StatisticalUnit canonicalUnit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected FactStatisticalContext() {}

    public static FactStatisticalContext verified(Fact fact, StatisticalSeries series,
            StatisticalUnit unit, OffsetDateTime now) {
        if (fact == null || fact.getId() == null || series == null || series.getId() == null
                || unit != StatisticalUnit.KRW_BILLION || now == null || !series.isActive()
                || fact.getPredicate() != FactPredicate.REAL_GDP
                || fact.getValueType() != FactValueType.NUMBER
                || fact.getCurrencyCode() != null || fact.getEvent() != null
                || fact.getPeriodStart() == null || fact.getPeriodEnd() == null
                || series.getMetric() != StatisticalMetric.REAL_GDP
                || series.getFrequency() != StatisticalFrequency.QUARTERLY
                || series.getAdjustment() != StatisticalAdjustment.SEASONALLY_ADJUSTED
                || series.getValueKind() != StatisticalValueKind.LEVEL
                || series.getSubjectEntity() == null
                || series.getSubjectEntity().getId() == null
                || fact.getSubjectEntity() == null
                || fact.getSubjectEntity().getEntityType() != EntityType.COUNTRY
                || !series.getSubjectEntity().getId().equals(fact.getSubjectEntity().getId())) {
            throw new IllegalArgumentException("Statistical fact context does not match the fact and series");
        }
        FactStatisticalContext context = new FactStatisticalContext();
        context.factId = fact.getId();
        context.fact = fact;
        context.statisticalSeries = series;
        context.canonicalUnit = unit;
        context.createdAt = now;
        return context;
    }

    public UUID getFactId() { return factId; }
    public Fact getFact() { return fact; }
    public StatisticalSeries getStatisticalSeries() { return statisticalSeries; }
    public StatisticalUnit getCanonicalUnit() { return canonicalUnit; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
