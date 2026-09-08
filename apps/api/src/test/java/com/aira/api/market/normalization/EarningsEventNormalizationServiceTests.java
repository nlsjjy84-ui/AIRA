package com.aira.api.market.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aira.api.market.domain.*;
import com.aira.api.market.repository.*;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EarningsEventNormalizationServiceTests {
    @Test
    void attachesRequiredRelationsBeforeConfirmingTheCandidate() throws Exception {
        EventRepository events = mock(EventRepository.class);
        EventEntityRepository eventEntities = mock(EventEntityRepository.class);
        EventEvidenceRepository eventEvidence = mock(EventEvidenceRepository.class);
        MarketEntityRepository entities = mock(MarketEntityRepository.class);
        EvidenceRepository evidenceRepository = mock(EvidenceRepository.class);
        var service = new EarningsEventNormalizationService(events, eventEntities, eventEvidence,
                entities, evidenceRepository);
        MarketEntity company = instance(MarketEntity.class);
        set(company, "id", UUID.randomUUID());
        set(company, "entityType", EntityType.COMPANY);
        set(company, "canonicalKey", "COMPANY:test");
        Evidence evidence = instance(Evidence.class);
        set(evidence, "id", UUID.randomUUID());
        set(evidence, "collectedAt", OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        when(entities.findById(company.getId())).thenReturn(Optional.of(company));
        when(evidenceRepository.findById(evidence.getId())).thenReturn(Optional.of(evidence));
        when(events.findByDedupKey(any())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event.getId() == null) set(event, "id", UUID.randomUUID());
            return event;
        });
        when(eventEntities.findById(any())).thenReturn(Optional.empty());
        when(eventEvidence.findById(any())).thenReturn(Optional.empty());

        Event result = service.normalize(new EarningsNormalizationInput(company.getId(),
                evidence.getId(), LocalDate.parse("2025-12-31"), "neutral title",
                OffsetDateTime.parse("2025-12-31T00:00:00Z")));

        assertEquals(EventStatus.CONFIRMED, result.getStatus());
        var order = inOrder(eventEntities, eventEvidence, events);
        order.verify(events).saveAndFlush(any(Event.class));
        order.verify(eventEntities).saveAndFlush(any(EventEntity.class));
        order.verify(eventEvidence).saveAndFlush(any(EventEvidence.class));
        order.verify(events).saveAndFlush(any(Event.class));
    }

    private static <T> T instance(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
