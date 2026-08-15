package com.aira.api.delivery.domain;

import com.aira.api.analysis.domain.Assessment;
import com.aira.api.analysis.domain.Impact;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "briefing_item")
public class BriefingItem {
    @EmbeddedId
    private BriefingItemId id;

    @MapsId("briefingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "briefing_id", nullable = false)
    private Briefing briefing;

    @MapsId("assessmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_id")
    private Impact impact;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BriefingItem() {}

    public BriefingItemId getId() { return id; }
    public Briefing getBriefing() { return briefing; }
    public Assessment getAssessment() { return assessment; }
    public Impact getImpact() { return impact; }
    public short getDisplayOrder() { return displayOrder; }
    public String getReasonCode() { return reasonCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
