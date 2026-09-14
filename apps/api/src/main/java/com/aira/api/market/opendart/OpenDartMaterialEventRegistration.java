package com.aira.api.market.opendart;

import com.aira.api.market.domain.*;
import com.aira.api.market.repository.*;
import com.aira.api.market.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenDartMaterialEventRegistration {
    private final EntityExternalIdentifierRegistryService identifiers;
    private final SourceRegistryService sources;
    private final EvidenceRegistrationStore evidenceRegistrations;
    private final EvidenceRepository evidences;
    private final EventRegistrationStore registrations;
    private final EventRepository events;
    private final EventEntityRepository eventEntities;
    private final EventEvidenceRepository eventEvidences;
    private final JdbcTemplate jdbc;

    public OpenDartMaterialEventRegistration(EntityExternalIdentifierRegistryService identifiers,
            SourceRegistryService sources, EvidenceRegistrationStore evidenceRegistrations,
            EvidenceRepository evidences, EventRegistrationStore registrations, EventRepository events,
            EventEntityRepository eventEntities, EventEvidenceRepository eventEvidences, JdbcTemplate jdbc) {
        this.identifiers = identifiers;
        this.sources = sources;
        this.evidenceRegistrations = evidenceRegistrations;
        this.evidences = evidences;
        this.registrations = registrations;
        this.events = events;
        this.eventEntities = eventEntities;
        this.eventEvidences = eventEvidences;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID register(ValidatedMaterialEvent input) {
        if (input == null) throw new IllegalArgumentException("Validated material input is required");
        // Material v1 fields are date-only; receipt, request and observation times are not occurrence timestamps.
        OffsetDateTime occurredAt = null;
        MarketEntity company = identifiers.findEntity(new ExternalIdentifierKey(
                "OPENDART", "CORP_CODE", input.corpCode())).orElseThrow(
                () -> new IllegalStateException("Canonical OpenDART company is required"));
        if (company.getEntityType() != EntityType.COMPANY) throw new IllegalStateException("Material subject must be COMPANY");
        String expectedIdentity = "OPENDART_MATERIAL:" + input.endpointKey() + ":" + input.receiptNumber();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "OPENDART:MATERIAL:" + input.receiptNumber());
        Integer otherEndpoint = jdbc.queryForObject("""
                SELECT count(*) FROM evidence e JOIN source s ON s.id=e.source_id
                WHERE s.source_type='REGULATOR' AND s.external_key='opendart'
                  AND e.external_id LIKE ? AND e.external_id<>?
                """, Integer.class, "OPENDART_MATERIAL:%:" + input.receiptNumber(), expectedIdentity);
        if (otherEndpoint != null && otherEndpoint != 0)
            throw new IllegalStateException("Receipt has conflicting material endpoint classification");

        Source source = sources.registerOrReuse(new SourceRegistration(SourceType.REGULATOR,
                "opendart", "OpenDART", "opendart.fss.or.kr"));
        if (source.getSourceType() != SourceType.REGULATOR || !"opendart".equals(source.getExternalKey())
                || !"OpenDART".equals(source.getName())
                || !"opendart.fss.or.kr".equals(source.getCanonicalDomain())) {
            throw new IllegalStateException("OpenDART source provenance conflicts");
        }
        UUID evidenceId = evidenceRegistrations.registerOrGetId(source, input.structuredEvidence());
        Evidence evidence = evidences.findById(evidenceId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        byte[] dedupKey = dedup(input.endpointKey(), company.getCanonicalKey(), input.receiptNumber());
        Event event = registrations.registerGenericOrGetLocked(Event.createGeneric(input.eventType(),
                input.neutralTitle(), occurredAt, input.observedAt(), dedupKey, now, input.origin()));
        if (event.getEventType() != input.eventType() || !event.getTitle().equals(input.neutralTitle()))
            throw new IllegalStateException("Generic event dedup identity conflicts with type or title");
        event.observeAt(input.observedAt(), now);

        EventEntityId subjectId = new EventEntityId(event.getId(), company.getId());
        var subject = eventEntities.findById(subjectId);
        if (subject.isPresent() && !"SUBJECT".equals(subject.get().getRelationType()))
            throw new IllegalStateException("Material company relation conflicts");
        if (subject.isEmpty()) eventEntities.saveAndFlush(EventEntity.subject(event, company, now));
        EventEvidenceId supportId = new EventEvidenceId(event.getId(), evidence.getId());
        var support = eventEvidences.findById(supportId);
        if (support.isPresent() && !"SUPPORTS".equals(support.get().getRelationType()))
            throw new IllegalStateException("Material evidence relation conflicts");
        if (support.isEmpty()) eventEvidences.saveAndFlush(EventEvidence.supports(event, evidence, now));
        event.confirm(true, true, now);
        return events.saveAndFlush(event).getId();
    }

    static byte[] dedup(String endpointKey, String companyCanonicalKey, String receipt) {
        String identity = "AIRA|EVENT|V1|OPENDART_MATERIAL|" + endpointKey + "|"
                + companyCanonicalKey + "|" + receipt;
        try { return MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
