package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_entity")
public class EventEntity {
    @EmbeddedId
    private EventEntityId id;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @MapsId("entityId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private MarketEntity marketEntity;

    @Column(name = "relation_type", nullable = false, length = 24)
    private String relationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Relevance relevance;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EventEntity() {}

    public EventEntityId getId() { return id; }
    public Event getEvent() { return event; }
    public MarketEntity getMarketEntity() { return marketEntity; }
    public String getRelationType() { return relationType; }
    public Relevance getRelevance() { return relevance; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
