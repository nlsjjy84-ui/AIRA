package com.aira.api.analysis.domain;

import com.aira.api.market.domain.MarketEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "impact")
public class Impact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private MarketEntity marketEntity;

    @Column(name = "sequence_no", nullable = false)
    private short sequenceNo;

    @Column(nullable = false, columnDefinition = "text")
    private String factor;

    @Column(name = "transmission_path", nullable = false, columnDefinition = "text")
    private String transmissionPath;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImpactDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_horizon", nullable = false, length = 24)
    private TimeHorizon timeHorizon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Confidence confidence;

    @Column(columnDefinition = "text")
    private String uncertainty;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Impact() {}

    public UUID getId() { return id; }
    public Assessment getAssessment() { return assessment; }
    public MarketEntity getMarketEntity() { return marketEntity; }
    public short getSequenceNo() { return sequenceNo; }
    public String getFactor() { return factor; }
    public String getTransmissionPath() { return transmissionPath; }
    public String getRationale() { return rationale; }
    public ImpactDirection getDirection() { return direction; }
    public TimeHorizon getTimeHorizon() { return timeHorizon; }
    public Confidence getConfidence() { return confidence; }
    public String getUncertainty() { return uncertainty; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
