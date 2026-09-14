package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FactPeriodEvidenceId implements Serializable {
    @Column(name = "fact_id", nullable = false)
    private UUID factId;
    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    protected FactPeriodEvidenceId() {}

    public FactPeriodEvidenceId(UUID factId, UUID evidenceId) {
        if (factId == null || evidenceId == null) {
            throw new IllegalArgumentException("Fact and evidence identifiers are required");
        }
        this.factId = factId;
        this.evidenceId = evidenceId;
    }

    public UUID getFactId() { return factId; }
    public UUID getEvidenceId() { return evidenceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FactPeriodEvidenceId that)) return false;
        return Objects.equals(factId, that.factId) && Objects.equals(evidenceId, that.evidenceId);
    }

    @Override
    public int hashCode() { return Objects.hash(factId, evidenceId); }
}
