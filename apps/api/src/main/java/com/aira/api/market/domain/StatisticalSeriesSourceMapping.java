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
@Table(name = "statistical_series_source_mapping")
public class StatisticalSeriesSourceMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statistical_series_id", nullable = false)
    private StatisticalSeries statisticalSeries;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "provider_binding_key", nullable = false, length = 512)
    private String providerBindingKey;

    @Column(name = "provider_series_name", nullable = false, columnDefinition = "text")
    private String providerSeriesName;

    @Column(name = "provider_item_name", nullable = false, columnDefinition = "text")
    private String providerItemName;

    @Column(name = "provider_frequency_code", nullable = false, length = 32)
    private String providerFrequencyCode;

    @Column(name = "provider_unit_name", nullable = false, length = 100)
    private String providerUnitName;

    @Column(name = "metadata_locator", nullable = false, columnDefinition = "text")
    private String metadataLocator;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StatisticalSeriesSourceMapping() {}

    public UUID getId() { return id; }
    public StatisticalSeries getStatisticalSeries() { return statisticalSeries; }
    public Source getSource() { return source; }
    public String getProviderBindingKey() { return providerBindingKey; }
    public String getProviderSeriesName() { return providerSeriesName; }
    public String getProviderItemName() { return providerItemName; }
    public String getProviderFrequencyCode() { return providerFrequencyCode; }
    public String getProviderUnitName() { return providerUnitName; }
    public String getMetadataLocator() { return metadataLocator; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
