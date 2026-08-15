package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventEvidenceId implements Serializable {
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    protected EventEvidenceId() {}

    public EventEvidenceId(UUID eventId, UUID evidenceId) {
        this.eventId = eventId;
        this.evidenceId = evidenceId;
    }

    public UUID getEventId() { return eventId; }
    public UUID getEvidenceId() { return evidenceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EventEvidenceId that)) return false;
        return Objects.equals(eventId, that.eventId) && Objects.equals(evidenceId, that.evidenceId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId, evidenceId); }
}
