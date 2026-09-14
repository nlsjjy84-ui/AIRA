package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fact_period_evidence")
public class FactPeriodEvidence {
    @EmbeddedId
    private FactPeriodEvidenceId id;
    @MapsId("factId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fact_id", nullable = false)
    private Fact fact;
    @MapsId("evidenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;
    @Column(nullable = false, columnDefinition = "text")
    private String locator;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected FactPeriodEvidence() {}

    // The caller must verify these dates against this Evidence before linking.
    // This factory checks agreement with the canonical Fact period, not the source document.
    public static FactPeriodEvidence verified(Fact fact, Evidence evidence, String locator,
            LocalDate verifiedStart, LocalDate verifiedEnd, OffsetDateTime now) {
        if (fact == null || fact.getId() == null || evidence == null || evidence.getId() == null
                || locator == null || locator.isBlank() || now == null
                || verifiedStart == null || verifiedEnd == null
                || verifiedEnd.isBefore(verifiedStart)
                || !verifiedStart.equals(fact.getPeriodStart())
                || !verifiedEnd.equals(fact.getPeriodEnd())) {
            throw new IllegalArgumentException("Verified evidence period must match the fact period");
        }
        FactPeriodEvidence link = new FactPeriodEvidence();
        link.id = new FactPeriodEvidenceId(fact.getId(), evidence.getId());
        link.fact = fact;
        link.evidence = evidence;
        link.locator = locator;
        link.createdAt = now;
        return link;
    }

    public FactPeriodEvidenceId getId() { return id; }
    public Fact getFact() { return fact; }
    public Evidence getEvidence() { return evidence; }
    public String getLocator() { return locator; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
