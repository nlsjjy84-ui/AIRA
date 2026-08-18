package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FactAssertionId implements Serializable {
    @Column(name = "fact_id", nullable = false)
    private UUID factId;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    protected FactAssertionId() {}

    public FactAssertionId(UUID factId, UUID evidenceId) {
        this.factId = factId;
        this.evidenceId = evidenceId;
    }

    public UUID getFactId() { return factId; }
    public UUID getEvidenceId() { return evidenceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FactAssertionId that)) return false;
        return Objects.equals(factId, that.factId) && Objects.equals(evidenceId, that.evidenceId);
    }

    @Override
    public int hashCode() { return Objects.hash(factId, evidenceId); }
}
