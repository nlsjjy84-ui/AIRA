package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "entity_external_identifier")
public class EntityExternalIdentifier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private MarketEntity entity;

    @Column(nullable = false, length = 64)
    private String namespace;

    @Column(name = "identifier_type", nullable = false, length = 64)
    private String identifierType;

    @Column(name = "identifier_value", nullable = false, length = 255)
    private String identifierValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EntityExternalIdentifier() {}

    public UUID getId() { return id; }
    public MarketEntity getEntity() { return entity; }
    public String getNamespace() { return namespace; }
    public String getIdentifierType() { return identifierType; }
    public String getIdentifierValue() { return identifierValue; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
