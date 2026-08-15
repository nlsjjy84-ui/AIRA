package com.aira.api.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BriefingItemId implements Serializable {
    @Column(name = "briefing_id", nullable = false)
    private UUID briefingId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    protected BriefingItemId() {}

    public BriefingItemId(UUID briefingId, UUID assessmentId) {
        this.briefingId = briefingId;
        this.assessmentId = assessmentId;
    }

    public UUID getBriefingId() { return briefingId; }
    public UUID getAssessmentId() { return assessmentId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BriefingItemId that)) return false;
        return Objects.equals(briefingId, that.briefingId)
                && Objects.equals(assessmentId, that.assessmentId);
    }

    @Override
    public int hashCode() { return Objects.hash(briefingId, assessmentId); }
}
