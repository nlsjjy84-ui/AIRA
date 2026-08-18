package com.aira.api.market.normalization;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.EventEntity;
import com.aira.api.market.domain.EventEntityId;
import com.aira.api.market.domain.EventEvidence;
import com.aira.api.market.domain.EventEvidenceId;
import com.aira.api.market.domain.EventType;
import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactAssertionId;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.EventEntityRepository;
import com.aira.api.market.repository.EventEvidenceRepository;
import com.aira.api.market.repository.EventRepository;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.repository.FactAssertionRepository;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarningsFactNormalizationService {
    private final EventRepository events;
    private final MarketEntityRepository entities;
    private final EvidenceRepository evidence;
    private final EventEntityRepository eventEntities;
    private final EventEvidenceRepository eventEvidence;
    private final FactRepository facts;
    private final FactAssertionRepository assertions;

    public EarningsFactNormalizationService(EventRepository events,
            MarketEntityRepository entities, EvidenceRepository evidence,
            EventEntityRepository eventEntities, EventEvidenceRepository eventEvidence,
            FactRepository facts, FactAssertionRepository assertions) {
        this.events = events;
        this.entities = entities;
        this.evidence = evidence;
        this.eventEntities = eventEntities;
        this.eventEvidence = eventEvidence;
        this.facts = facts;
        this.assertions = assertions;
    }

    @Transactional
    public Fact normalize(EarningsFactNormalizationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Earnings fact normalization input is required");
        }
        Event event = events.findById(input.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event was not found"));
        if (event.getEventType() != EventType.EARNINGS) {
            throw new IllegalArgumentException("Fact event must be earnings");
        }
        MarketEntity subject = entities.findById(input.subjectEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Subject entity was not found"));
        if (subject.getEntityType() != EntityType.COMPANY) {
            throw new IllegalArgumentException("Earnings fact subject must be a company");
        }
        Evidence supportingEvidence = evidence.findById(input.evidenceId())
                .orElseThrow(() -> new IllegalArgumentException("Evidence was not found"));
        EventEntity eventSubject = eventEntities.findById(
                        new EventEntityId(event.getId(), subject.getId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company is not linked to the earnings event"));
        if (!"SUBJECT".equals(eventSubject.getRelationType())) {
            throw new IllegalArgumentException("Company is not the earnings event subject");
        }
        EventEvidence supportingLink = eventEvidence.findById(
                        new EventEvidenceId(event.getId(), supportingEvidence.getId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Evidence is not linked to the earnings event"));
        if (!"SUPPORTS".equals(supportingLink.getRelationType())) {
            throw new IllegalArgumentException("Evidence does not support the earnings event");
        }

        byte[] dedupKey = EarningsFactDedupKey.create(input.predicate(),
                subject.getCanonicalKey(), input.periodStart(), input.periodEnd(),
                input.currencyCode());
        Fact fact = facts.findByDedupKey(dedupKey).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (fact == null) {
            fact = facts.saveAndFlush(Fact.supportedNumber(subject, event, input.predicate(),
                    input.numberValue(), input.currencyCode(), input.periodStart(),
                    input.periodEnd(), dedupKey, now));
        }

        FactAssertionId assertionId = new FactAssertionId(fact.getId(),
                supportingEvidence.getId());
        if (assertions.existsById(assertionId)) {
            return fact;
        }

        if (fact.getStatus() == FactStatus.SUPPORTED
                && fact.getValueNumber().compareTo(input.numberValue()) != 0) {
            fact.markConflicting(now);
        } else if (fact.getStatus() == FactStatus.UNKNOWN) {
            throw new IllegalStateException("Unknown facts cannot be resolved by v1 normalization");
        }
        assertions.save(FactAssertion.assertedNumber(fact, supportingEvidence,
                input.locator(), input.numberValue(), now));
        return fact;
    }
}
