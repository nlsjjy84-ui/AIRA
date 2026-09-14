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
import com.aira.api.market.repository.EventRegistrationStore;
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
    private final EventRegistrationStore registrations;

    public EarningsEventNormalizationService(EventRepository events,
            EventEntityRepository eventEntities, EventEvidenceRepository eventEvidence,
            MarketEntityRepository entities, EvidenceRepository evidence,
            EventRegistrationStore registrations) {
        this.events = events;
        this.eventEntities = eventEntities;
        this.eventEvidence = eventEvidence;
        this.entities = entities;
        this.evidence = evidence;
        this.registrations = registrations;
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
        Event event = registrations.registerOrGetLocked(Event.createEarnings(input.neutralTitle(),
                input.occurredAt(), supportingEvidence.getCollectedAt(), dedupKey, now, input.origin()));
        // Compare against the refreshed, locked row; older observations never move it back.
        event.observeAt(supportingEvidence.getCollectedAt(), now);

        EventEntityId entityLinkId = new EventEntityId(event.getId(), subject.getId());
        var existingEntityLink = eventEntities.findById(entityLinkId);
        if (existingEntityLink.isPresent()
                && !"SUBJECT".equals(existingEntityLink.get().getRelationType())) {
            throw new IllegalStateException("Company is not the event subject");
        }
        if (existingEntityLink.isEmpty()) {
            eventEntities.saveAndFlush(EventEntity.subject(event, subject, now));
        }
        EventEvidenceId evidenceLinkId = new EventEvidenceId(
                event.getId(), supportingEvidence.getId());
        var existingEvidenceLink = eventEvidence.findById(evidenceLinkId);
        if (existingEvidenceLink.isPresent()
                && !"SUPPORTS".equals(existingEvidenceLink.get().getRelationType())) {
            throw new IllegalStateException("Evidence does not support the event");
        }
        if (existingEvidenceLink.isEmpty()) {
            eventEvidence.saveAndFlush(EventEvidence.supports(event, supportingEvidence, now));
        }
        event.confirm(true, true, now);
        return events.saveAndFlush(event);
    }
}
