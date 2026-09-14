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

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_origin", nullable = false, length = 24, updatable = false)
    private EventOrigin ingestionOrigin;

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

    public static Event createEarnings(String title, OffsetDateTime occurredAt,
            OffsetDateTime observedAt, byte[] dedupKey, OffsetDateTime now) {
        return createEarnings(title, occurredAt, observedAt, dedupKey, now, EventOrigin.LEGACY_UNKNOWN);
    }

    public static Event createEarnings(String title, OffsetDateTime occurredAt,
            OffsetDateTime observedAt, byte[] dedupKey, OffsetDateTime now, EventOrigin origin) {
        if (title == null || title.isBlank() || observedAt == null
                || dedupKey == null || dedupKey.length == 0 || now == null || origin == null) {
            throw new IllegalArgumentException("Earnings event creation values are required");
        }
        Event event = new Event();
        event.eventType = EventType.EARNINGS;
        event.title = title;
        event.occurredAt = occurredAt;
        event.occurredUntil = null;
        event.firstObservedAt = observedAt;
        event.lastObservedAt = observedAt;
        event.status = EventStatus.CANDIDATE;
        event.ingestionOrigin = origin;
        event.dedupKey = dedupKey.clone();
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public static Event createGeneric(EventType eventType, String title, OffsetDateTime occurredAt,
            OffsetDateTime observedAt, byte[] dedupKey, OffsetDateTime now) {
        return createGeneric(eventType, title, occurredAt, observedAt, dedupKey, now, EventOrigin.LEGACY_UNKNOWN);
    }

    public static Event createGeneric(EventType eventType, String title, OffsetDateTime occurredAt,
            OffsetDateTime observedAt, byte[] dedupKey, OffsetDateTime now, EventOrigin origin) {
        if (eventType == null || eventType == EventType.EARNINGS || title == null || title.isBlank()
                || observedAt == null || dedupKey == null || dedupKey.length == 0 || now == null || origin == null) {
            throw new IllegalArgumentException("Generic event creation values are required");
        }
        Event event = new Event();
        event.eventType = eventType;
        event.title = title;
        event.occurredAt = occurredAt;
        event.firstObservedAt = observedAt;
        event.lastObservedAt = observedAt;
        event.status = EventStatus.CANDIDATE;
        event.ingestionOrigin = origin;
        event.dedupKey = dedupKey.clone();
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public void observeAt(OffsetDateTime observedAt, OffsetDateTime now) {
        if (observedAt == null || now == null) {
            throw new IllegalArgumentException("Event observation values are required");
        }
        if (status == EventStatus.MERGED || status == EventStatus.DISCARDED) {
            throw new IllegalStateException("Merged or discarded events cannot be re-observed");
        }
        if (observedAt.isAfter(lastObservedAt)) {
            lastObservedAt = observedAt;
            updatedAt = now;
        }
    }

    public void confirm(boolean hasCanonicalEntity, boolean hasEvidence, OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Event confirmation time is required");
        }
        if (!hasCanonicalEntity || !hasEvidence) {
            throw new IllegalStateException(
                    "Confirmed events require a canonical entity and evidence");
        }
        if (status == EventStatus.CONFIRMED) {
            return;
        }
        if (status != EventStatus.CANDIDATE) {
            throw new IllegalStateException("Only candidate events can be confirmed");
        }
        status = EventStatus.CONFIRMED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public String getTitle() { return title; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getOccurredUntil() { return occurredUntil; }
    public OffsetDateTime getFirstObservedAt() { return firstObservedAt; }
    public OffsetDateTime getLastObservedAt() { return lastObservedAt; }
    public EventStatus getStatus() { return status; }
    public EventOrigin getIngestionOrigin() { return ingestionOrigin; }
    public byte[] getDedupKey() { return dedupKey == null ? null : dedupKey.clone(); }
    public Event getSupersededByEvent() { return supersededByEvent; }
    public String getMergeReason() { return mergeReason; }
    public OffsetDateTime getMergedAt() { return mergedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<EventEvidence> getEvidenceLinks() { return evidenceLinks; }
    public Set<EventEntity> getEntityLinks() { return entityLinks; }
}
