package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 32)
    private EvidenceType evidenceType;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(columnDefinition = "text")
    private String title;

    @Column(name = "content_hash", nullable = false)
    private byte[] contentHash;

    @Column(columnDefinition = "text")
    private String locator;

    @Column(columnDefinition = "text")
    private String excerpt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    @Column(nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvidenceStatus status;

    @OneToMany(mappedBy = "evidence", fetch = FetchType.LAZY)
    private Set<EventEvidence> eventLinks = new HashSet<>();

    protected Evidence() {}

    public UUID getId() { return id; }
    public Source getSource() { return source; }
    public EvidenceType getEvidenceType() { return evidenceType; }
    public String getExternalId() { return externalId; }
    public String getOriginalUrl() { return originalUrl; }
    public String getTitle() { return title; }
    public byte[] getContentHash() { return contentHash == null ? null : contentHash.clone(); }
    public String getLocator() { return locator; }
    public String getExcerpt() { return excerpt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public int getRevision() { return revision; }
    public EvidenceStatus getStatus() { return status; }
    public Set<EventEvidence> getEventLinks() { return eventLinks; }
}
