package com.aira.api.market.service;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.repository.SourceRepository;
import com.aira.api.market.repository.StatisticalSeriesRegistrationStore;
import com.aira.api.market.repository.StatisticalSeriesRepository;
import com.aira.api.market.repository.StatisticalSeriesSourceMappingRegistrationStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticalSeriesRegistryService {
    private final MarketEntityRepository entities;
    private final SourceRepository sources;
    private final StatisticalSeriesRepository series;
    private final StatisticalSeriesRegistrationStore seriesStore;
    private final StatisticalSeriesSourceMappingRegistrationStore mappingStore;

    public StatisticalSeriesRegistryService(
            MarketEntityRepository entities,
            SourceRepository sources,
            StatisticalSeriesRepository series,
            StatisticalSeriesRegistrationStore seriesStore,
            StatisticalSeriesSourceMappingRegistrationStore mappingStore) {
        this.entities = entities;
        this.sources = sources;
        this.series = series;
        this.seriesStore = seriesStore;
        this.mappingStore = mappingStore;
    }

    @Transactional
    public UUID registerSeries(StatisticalSeriesRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Statistical series registration is required");
        }
        var subject = entities.findById(registration.subjectEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Statistical series subject not found"));
        if (!subject.isActive()) {
            throw new IllegalStateException("Statistical series subject is inactive");
        }
        if (registration.metric() == StatisticalMetric.REAL_GDP
                && subject.getEntityType() != EntityType.COUNTRY) {
            throw new IllegalArgumentException("REAL_GDP subject must be a COUNTRY entity");
        }
        return seriesStore.registerOrGetId(registration);
    }

    @Transactional
    public UUID registerSourceMapping(StatisticalSeriesSourceMappingRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Statistical series source mapping is required");
        }
        var statisticalSeries = series.findById(registration.statisticalSeriesId())
                .orElseThrow(() -> new IllegalArgumentException("Statistical series not found"));
        if (!statisticalSeries.isActive()) {
            throw new IllegalStateException("Statistical series is inactive");
        }
        var source = sources.findById(registration.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
        if (!source.isActive()) {
            throw new IllegalStateException("Source is inactive");
        }
        return mappingStore.registerOrGetId(registration);
    }
}
