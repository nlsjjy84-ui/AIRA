package com.aira.api.market.service;

import com.aira.api.market.domain.FactStatisticalContext;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.FactStatisticalContextRegistrationStore;
import com.aira.api.market.repository.FactStatisticalContextRepository;
import com.aira.api.market.repository.StatisticalSeriesRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticalFactContextRegistryService {
    private final FactRepository facts;
    private final StatisticalSeriesRepository series;
    private final FactStatisticalContextRepository contexts;
    private final FactStatisticalContextRegistrationStore store;

    public StatisticalFactContextRegistryService(
            FactRepository facts,
            StatisticalSeriesRepository series,
            FactStatisticalContextRepository contexts,
            FactStatisticalContextRegistrationStore store) {
        this.facts = facts;
        this.series = series;
        this.contexts = contexts;
        this.store = store;
    }
    @Transactional
    public FactStatisticalContext registerOrReuse(
            StatisticalFactContextRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Statistical fact context registration is required");
        }
        var fact = facts.findById(registration.factId())
                .orElseThrow(() -> new IllegalArgumentException("Statistical fact was not found"));
        var statisticalSeries = series.findById(registration.statisticalSeriesId())
                .orElseThrow(() -> new IllegalArgumentException("Statistical series was not found"));

        FactStatisticalContext.verified(
                fact,
                statisticalSeries,
                registration.canonicalUnit(),
                OffsetDateTime.now(ZoneOffset.UTC));

        var factId = store.registerOrGetFactId(registration);
        return contexts.findById(factId)
                .orElseThrow(() -> new IllegalStateException(
                        "Registered fact statistical context was not found"));
    }
}
