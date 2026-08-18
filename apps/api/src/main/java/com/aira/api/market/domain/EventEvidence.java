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

    public static EventEvidence supports(Event event, Evidence evidence, OffsetDateTime now) {
        if (event == null || event.getId() == null || evidence == null
                || evidence.getId() == null || now == null) {
            throw new IllegalArgumentException("Event evidence link values are required");
        }
        EventEvidence link = new EventEvidence();
        link.id = new EventEvidenceId(event.getId(), evidence.getId());
        link.event = event;
        link.evidence = evidence;
        link.relationType = "SUPPORTS";
        link.linkedAt = now;
        return link;
    }

    public EventEvidenceId getId() { return id; }
    public Event getEvent() { return event; }
    public Evidence getEvidence() { return evidence; }
    public String getRelationType() { return relationType; }
    public OffsetDateTime getLinkedAt() { return linkedAt; }
}
