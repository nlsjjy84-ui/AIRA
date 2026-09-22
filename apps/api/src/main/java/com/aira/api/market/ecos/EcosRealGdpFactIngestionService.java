package com.aira.api.market.ecos;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactAssertionId;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.domain.FactValueType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalSeries;
import com.aira.api.market.domain.StatisticalUnit;
import com.aira.api.market.domain.StatisticalValueKind;
import com.aira.api.market.normalization.StatisticalFactDedupKey;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.repository.FactAssertionRepository;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.repository.StatisticalSeriesRepository;
import com.aira.api.market.service.StatisticalFactContextRegistration;
import com.aira.api.market.service.StatisticalFactContextRegistryService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EcosRealGdpFactIngestionService {
    private final EcosRealGdpSeriesBindingService bindings;
    private final EcosRealGdpEvidenceRegistrationService evidenceRegistrations;
    private final EvidenceRepository evidence;
    private final MarketEntityRepository entities;
    private final StatisticalSeriesRepository series;
    private final FactRepository facts;
    private final FactAssertionRepository assertions;
    private final StatisticalFactContextRegistryService contexts;

    public EcosRealGdpFactIngestionService(
            EcosRealGdpSeriesBindingService bindings,
            EcosRealGdpEvidenceRegistrationService evidenceRegistrations,
            EvidenceRepository evidence,
            MarketEntityRepository entities,
            StatisticalSeriesRepository series,
            FactRepository facts,
            FactAssertionRepository assertions,
            StatisticalFactContextRegistryService contexts) {
        this.bindings = require(bindings);
        this.evidenceRegistrations = require(evidenceRegistrations);
        this.evidence = require(evidence);
        this.entities = require(entities);
        this.series = require(series);
        this.facts = require(facts);
        this.assertions = require(assertions);
        this.contexts = require(contexts);
    }

    @Transactional
    public EcosRealGdpFactIngestionResult ingest(
            EcosRealGdpObservationResult result,
            OffsetDateTime collectedAt) {
        if (result == null || collectedAt == null) {
            throw new IllegalArgumentException("REAL_GDP observation result and collection time are required");
        }

        List<PreparedObservation> prepared = result.observations().stream()
                .map(EcosRealGdpFactIngestionService::prepare)
                .sorted(Comparator.comparing(item -> item.observation().time()))
                .toList();

        EcosRealGdpSeriesBindingResult binding = bindings.registerOrReuse();
        UUID evidenceId = evidenceRegistrations.register(result, collectedAt);
        Evidence savedEvidence = evidence.findById(evidenceId)
                .orElseThrow(() -> new IllegalStateException("Registered ECOS evidence was not found"));
        if (!binding.sourceId().equals(savedEvidence.getSource().getId())) {
            throw new IllegalStateException("ECOS evidence source does not match REAL_GDP binding source");
        }
        requireCanonicalEvidence(savedEvidence, result, collectedAt);

        MarketEntity country = entities.findById(binding.countryEntityId())
                .orElseThrow(() -> new IllegalStateException("REAL_GDP country entity was not found"));
        StatisticalSeries statisticalSeries = series.findById(binding.statisticalSeriesId())
                .orElseThrow(() -> new IllegalStateException("REAL_GDP statistical series was not found"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> factIds = new ArrayList<>(prepared.size());
        for (PreparedObservation item : prepared) {
            byte[] dedupKey = StatisticalFactDedupKey.create(
                    country.getCanonicalKey(),
                    StatisticalMetric.REAL_GDP,
                    StatisticalFrequency.QUARTERLY,
                    StatisticalAdjustment.SEASONALLY_ADJUSTED,
                    StatisticalValueKind.LEVEL,
                    StatisticalUnit.KRW_BILLION,
                    item.period().start(),
                    item.period().end());

            Fact fact = facts.findByDedupKey(dedupKey).orElse(null);
            if (fact == null) {
                fact = facts.saveAndFlush(Fact.supportedStatisticalNumber(
                        country,
                        FactPredicate.REAL_GDP,
                        item.observation().numericValue(),
                        item.period().start(),
                        item.period().end(),
                        dedupKey,
                        now));
            } else {
                requireCompatibleExistingFact(fact, country, item.period());
            }

            contexts.registerOrReuse(new StatisticalFactContextRegistration(
                    fact.getId(), statisticalSeries.getId(), StatisticalUnit.KRW_BILLION));
            attachAssertion(fact, savedEvidence, item.observation(), now);
            factIds.add(fact.getId());
        }

        return new EcosRealGdpFactIngestionResult(
                savedEvidence.getId(), statisticalSeries.getId(), factIds);
    }

    private void attachAssertion(
            Fact fact,
            Evidence supportingEvidence,
            EcosStatisticSearchObservation observation,
            OffsetDateTime now) {
        FactAssertionId assertionId = new FactAssertionId(
                fact.getId(), supportingEvidence.getId());
        if (assertions.existsById(assertionId)) {
            return;
        }

        if (fact.getStatus() == FactStatus.SUPPORTED) {
            if (fact.getValueNumber() == null) {
                throw new IllegalStateException("SUPPORTED REAL_GDP fact has no numeric value");
            }
            if (fact.getValueNumber().compareTo(observation.numericValue()) != 0) {
                fact.markConflicting(now);
            }
        } else if (fact.getStatus() != FactStatus.CONFLICTING) {
            throw new IllegalStateException("ECOS ingestion cannot resolve UNKNOWN REAL_GDP fact");
        }

        String locator = supportingEvidence.getLocator();
        if (locator == null || locator.isBlank()) {
            throw new IllegalStateException("ECOS evidence locator is required for fact assertion");
        }
        assertions.save(FactAssertion.assertedNumber(
                fact,
                supportingEvidence,
                locator + "#TIME=" + observation.time(),
                observation.numericValue(),
                now));
    }

    static void requireCanonicalEvidence(
            Evidence actual,
            EcosRealGdpObservationResult result,
            OffsetDateTime collectedAt) {
        var expected = EcosRealGdpEvidenceSnapshotFactory.create(result, collectedAt);
        if (actual == null
                || actual.getEvidenceType() != expected.evidenceType()
                || !java.util.Objects.equals(actual.getExternalId(), expected.externalId())
                || !java.util.Objects.equals(actual.getOriginalUrl(), expected.originalUrl())
                || !java.util.Objects.equals(actual.getTitle(), expected.title())
                || !java.util.Arrays.equals(actual.getContentHash(), expected.contentHash())
                || !java.util.Objects.equals(actual.getLocator(), expected.locator())
                || !java.util.Objects.equals(actual.getPublishedAt(), expected.publishedAt())
                || actual.getRevision() != expected.revision()) {
            throw new IllegalStateException(
                    "Registered ECOS evidence does not match validated REAL_GDP snapshot");
        }
    }

    private static PreparedObservation prepare(EcosStatisticSearchObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("ECOS observation must not be null");
        }
        if (observation.numericValue() == null) {
            throw new IllegalStateException(
                    "REAL_GDP observation without numeric DATA_VALUE cannot be ingested");
        }
        return new PreparedObservation(observation, EcosQuarterPeriod.from(observation.time()));
    }

    private static void requireCompatibleExistingFact(
            Fact fact, MarketEntity country, EcosQuarterPeriod period) {
        if (fact.getPredicate() != FactPredicate.REAL_GDP
                || fact.getValueType() != FactValueType.NUMBER
                || fact.getEvent() != null
                || fact.getCurrencyCode() != null
                || fact.getSubjectEntity() == null
                || fact.getSubjectEntity().getEntityType() != EntityType.COUNTRY
                || !country.getId().equals(fact.getSubjectEntity().getId())
                || !period.start().equals(fact.getPeriodStart())
                || !period.end().equals(fact.getPeriodEnd())
                || (fact.getStatus() != FactStatus.SUPPORTED
                    && fact.getStatus() != FactStatus.CONFLICTING)) {
            throw new IllegalStateException(
                    "Existing statistical fact conflicts with REAL_GDP dedup identity");
        }
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new IllegalArgumentException("ECOS fact ingestion dependency is required");
        }
        return value;
    }

    private record PreparedObservation(
            EcosStatisticSearchObservation observation,
            EcosQuarterPeriod period) {}
}
