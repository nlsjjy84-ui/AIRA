package com.aira.api.market.normalization;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.EventEntity;
import com.aira.api.market.domain.EventEntityId;
import com.aira.api.market.domain.EventEvidence;
import com.aira.api.market.domain.EventEvidenceId;
import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.EventEntityRepository;
import com.aira.api.market.repository.EventEvidenceRepository;
import com.aira.api.market.repository.EventRepository;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarningsEventNormalizationService {
    private final EventRepository events;
    private final EventEntityRepository eventEntities;
    private final EventEvidenceRepository eventEvidence;
    private final MarketEntityRepository entities;
    private final EvidenceRepository evidence;

    public EarningsEventNormalizationService(EventRepository events,
            EventEntityRepository eventEntities, EventEvidenceRepository eventEvidence,
            MarketEntityRepository entities, EvidenceRepository evidence) {
        this.events = events;
        this.eventEntities = eventEntities;
        this.eventEvidence = eventEvidence;
        this.entities = entities;
        this.evidence = evidence;
    }

    @Transactional
    public Event normalize(EarningsNormalizationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Earnings normalization input is required");
        }
        MarketEntity subject = entities.findById(input.subjectEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Subject entity was not found"));
        if (subject.getEntityType() != EntityType.COMPANY) {
            throw new IllegalArgumentException("Earnings subject must be a company");
        }
        Evidence supportingEvidence = evidence.findById(input.evidenceId())
                .orElseThrow(() -> new IllegalArgumentException("Evidence was not found"));

        byte[] dedupKey = EarningsEventDedupKey.create(
                subject.getCanonicalKey(), input.reportingPeriodEnd());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Event event = events.findByDedupKey(dedupKey).orElse(null);
        if (event == null) {
            event = events.saveAndFlush(Event.createEarnings(input.neutralTitle(),
                    input.occurredAt(), supportingEvidence.getCollectedAt(), dedupKey, now));
        } else {
            event.observeAt(supportingEvidence.getCollectedAt(), now);
        }

        EventEntityId entityLinkId = new EventEntityId(event.getId(), subject.getId());
        if (!eventEntities.existsById(entityLinkId)) {
            eventEntities.save(EventEntity.subject(event, subject, now));
        }
        EventEvidenceId evidenceLinkId = new EventEvidenceId(
                event.getId(), supportingEvidence.getId());
        if (!eventEvidence.existsById(evidenceLinkId)) {
            eventEvidence.save(EventEvidence.supports(event, supportingEvidence, now));
        }
        return event;
    }
}
