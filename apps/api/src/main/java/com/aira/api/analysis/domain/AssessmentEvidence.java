package com.aira.api.analysis.domain;

import com.aira.api.market.domain.Evidence;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "assessment_evidence")
public class AssessmentEvidence {
    @EmbeddedId
    private AssessmentEvidenceId id;

    @MapsId("assessmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @MapsId("evidenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(name = "usage_type", nullable = false, length = 24)
    private String usageType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AssessmentEvidence() {}

    public AssessmentEvidenceId getId() { return id; }
    public Assessment getAssessment() { return assessment; }
    public Evidence getEvidence() { return evidence; }
    public String getUsageType() { return usageType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
