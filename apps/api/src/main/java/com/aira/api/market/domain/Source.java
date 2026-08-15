package com.aira.api.market.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "source")
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "canonical_domain", length = 255)
    private String canonicalDomain;

    @Column(name = "external_key", length = 200)
    private String externalKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "source", fetch = FetchType.LAZY)
    private Set<Evidence> evidences = new HashSet<>();

    protected Source() {}

    public UUID getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getName() { return name; }
    public String getCanonicalDomain() { return canonicalDomain; }
    public String getExternalKey() { return externalKey; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<Evidence> getEvidences() { return evidences; }
}
