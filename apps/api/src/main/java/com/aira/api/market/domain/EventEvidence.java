package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_evidence")
public class EventEvidence {
    @EmbeddedId
    private EventEvidenceId id;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @MapsId("evidenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(name = "relation_type", nullable = false, length = 24)
    private String relationType;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    protected EventEvidence() {}

    public EventEvidenceId getId() { return id; }
    public Event getEvent() { return event; }
    public Evidence getEvidence() { return evidence; }
    public String getRelationType() { return relationType; }
    public OffsetDateTime getLinkedAt() { return linkedAt; }
}
