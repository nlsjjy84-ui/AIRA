package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "occurred_until")
    private OffsetDateTime occurredUntil;

    @Column(name = "first_observed_at", nullable = false)
    private OffsetDateTime firstObservedAt;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EventStatus status;

    @Column(name = "dedup_key")
    private byte[] dedupKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_event_id")
    private Event supersededByEvent;

    @Column(name = "merge_reason", columnDefinition = "text")
    private String mergeReason;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private Set<EventEvidence> evidenceLinks = new HashSet<>();

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private Set<EventEntity> entityLinks = new HashSet<>();

    protected Event() {}

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public String getTitle() { return title; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getOccurredUntil() { return occurredUntil; }
    public OffsetDateTime getFirstObservedAt() { return firstObservedAt; }
    public OffsetDateTime getLastObservedAt() { return lastObservedAt; }
    public EventStatus getStatus() { return status; }
    public byte[] getDedupKey() { return dedupKey == null ? null : dedupKey.clone(); }
    public Event getSupersededByEvent() { return supersededByEvent; }
    public String getMergeReason() { return mergeReason; }
    public OffsetDateTime getMergedAt() { return mergedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<EventEvidence> getEvidenceLinks() { return evidenceLinks; }
    public Set<EventEntity> getEntityLinks() { return entityLinks; }
}
