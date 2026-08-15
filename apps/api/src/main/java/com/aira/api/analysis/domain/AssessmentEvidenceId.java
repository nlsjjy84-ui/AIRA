package com.aira.api.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AssessmentEvidenceId implements Serializable {
    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    protected AssessmentEvidenceId() {}

    public AssessmentEvidenceId(UUID assessmentId, UUID evidenceId) {
        this.assessmentId = assessmentId;
        this.evidenceId = evidenceId;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public UUID getEvidenceId() { return evidenceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AssessmentEvidenceId that)) return false;
        return Objects.equals(assessmentId, that.assessmentId)
                && Objects.equals(evidenceId, that.evidenceId);
    }

    @Override
    public int hashCode() { return Objects.hash(assessmentId, evidenceId); }
}
