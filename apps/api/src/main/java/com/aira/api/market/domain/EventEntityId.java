package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventEntityId implements Serializable {
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    protected EventEntityId() {}

    public EventEntityId(UUID eventId, UUID entityId) {
        this.eventId = eventId;
        this.entityId = entityId;
    }

    public UUID getEventId() { return eventId; }
    public UUID getEntityId() { return entityId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EventEntityId that)) return false;
        return Objects.equals(eventId, that.eventId) && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId, entityId); }
}
