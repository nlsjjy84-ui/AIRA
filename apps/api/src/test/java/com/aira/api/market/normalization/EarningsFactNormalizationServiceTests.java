package com.aira.api.market.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.EventEntity;
import com.aira.api.market.domain.EventEvidence;
import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.domain.FactStatusReason;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.EventEntityRepository;
import com.aira.api.market.repository.EventEvidenceRepository;
import com.aira.api.market.repository.EventRepository;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.repository.FactAssertionRepository;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EarningsFactNormalizationServiceTests {
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 30);
    private static final OffsetDateTime INITIAL_TIME =
            OffsetDateTime.parse("2026-07-31T00:00:00Z");

    @Mock private EventRepository events;
    @Mock private MarketEntityRepository entities;
    @Mock private EvidenceRepository evidenceRepository;
    @Mock private EventEntityRepository eventEntities;
    @Mock private EventEvidenceRepository eventEvidence;
    @Mock private FactRepository facts;
    @Mock private FactAssertionRepository assertions;

    private EarningsFactNormalizationService service;
    private Event event;
    private MarketEntity subject;
    private Evidence evidence;
    private EventEntity eventSubject;
    private EventEvidence supportingLink;

    @BeforeEach
    void setUp() throws Exception {
        service = new EarningsFactNormalizationService(events, entities, evidenceRepository,
                eventEntities, eventEvidence, facts, assertions);
        event = Event.createEarnings("2026 Q2 results", INITIAL_TIME, INITIAL_TIME,
                new byte[32], INITIAL_TIME);
        set(event, "id", UUID.randomUUID());
        subject = newInstance(MarketEntity.class);
        set(subject, "id", UUID.randomUUID());
        set(subject, "entityType", EntityType.COMPANY);
        set(subject, "canonicalKey", "COMPANY:SAMSUNG-ELECTRONICS");
        evidence = newInstance(Evidence.class);
        set(evidence, "id", UUID.randomUUID());
        eventSubject = EventEntity.subject(event, subject, INITIAL_TIME);
        supportingLink = EventEvidence.supports(event, evidence, INITIAL_TIME);

        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(entities.findById(subject.getId())).thenReturn(Optional.of(subject));
        when(evidenceRepository.findById(evidence.getId())).thenReturn(Optional.of(evidence));
        when(eventEntities.findById(eventSubject.getId())).thenReturn(Optional.of(eventSubject));
        lenient().when(eventEvidence.findById(supportingLink.getId()))
                .thenReturn(Optional.of(supportingLink));
    }

    @Test
    void firstAssertionCreatesSupportedFactAndPreservesAssertion() throws Exception {
        when(facts.findByDedupKey(any())).thenReturn(Optional.empty());
        when(facts.saveAndFlush(any())).thenAnswer(invocation -> {
            Fact fact = invocation.getArgument(0);
            set(fact, "id", UUID.randomUUID());
            return fact;
        });
        when(assertions.existsById(any())).thenReturn(false);

        Fact result = service.normalize(input(new BigDecimal("100.00"), "table:revenue"));

        assertEquals(FactStatus.SUPPORTED, result.getStatus());
        assertEquals(new BigDecimal("100.00"), result.getValueNumber());
        assertEquals(result.getCreatedAt(), result.getUpdatedAt());
        ArgumentCaptor<FactAssertion> assertion = ArgumentCaptor.forClass(FactAssertion.class);
        verify(assertions).save(assertion.capture());
        assertEquals("table:revenue", assertion.getValue().getLocator());
        assertEquals(new BigDecimal("100.00"), assertion.getValue().getValueNumber());
    }

    @Test
    void equalValueWithDifferentScaleKeepsSupportedAndTimestamp() throws Exception {
        Fact existing = existingFact(new BigDecimal("100.0"));
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(facts.findByDedupKey(any())).thenReturn(Optional.of(existing));
        when(assertions.existsById(any())).thenReturn(false);

        Fact result = service.normalize(input(new BigDecimal("100.00"), "table:revenue"));

        assertEquals(FactStatus.SUPPORTED, result.getStatus());
        assertEquals(originalUpdatedAt, result.getUpdatedAt());
        verify(assertions).save(any(FactAssertion.class));
    }

    @Test
    void differentValueMarksFactConflictingAndClearsResolvedValue() throws Exception {
        Fact existing = existingFact(new BigDecimal("100.00"));
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(facts.findByDedupKey(any())).thenReturn(Optional.of(existing));
        when(assertions.existsById(any())).thenReturn(false);

        Fact result = service.normalize(input(new BigDecimal("101.00"), "table:revenue"));

        assertEquals(FactStatus.CONFLICTING, result.getStatus());
        assertEquals(FactStatusReason.ASSERTED_VALUE_CONFLICT, result.getStatusReason());
        assertNull(result.getValueNumber());
        org.junit.jupiter.api.Assertions.assertTrue(result.getUpdatedAt().isAfter(originalUpdatedAt));
        verify(assertions).save(any(FactAssertion.class));
    }

    @Test
    void sameEvidenceIsIdempotentAndDoesNotChangeTimestamp() throws Exception {
        Fact existing = existingFact(new BigDecimal("100.00"));
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        when(facts.findByDedupKey(any())).thenReturn(Optional.of(existing));
        when(assertions.existsById(any())).thenReturn(true);

        Fact result = service.normalize(input(new BigDecimal("999.00"), "other locator"));

        assertEquals(FactStatus.SUPPORTED, result.getStatus());
        assertEquals(new BigDecimal("100.00"), result.getValueNumber());
        assertEquals(originalUpdatedAt, result.getUpdatedAt());
        verify(assertions, never()).save(any());
    }

    @Test
    void alreadyConflictingFactStaysConflictingWhenAssertionIsAdded() throws Exception {
        Fact existing = existingFact(new BigDecimal("100.00"));
        existing.markConflicting(INITIAL_TIME.plusMinutes(1));
        OffsetDateTime conflictingAt = existing.getUpdatedAt();
        when(facts.findByDedupKey(any())).thenReturn(Optional.of(existing));
        when(assertions.existsById(any())).thenReturn(false);

        Fact result = service.normalize(input(new BigDecimal("102.00"), "table:revenue"));

        assertEquals(FactStatus.CONFLICTING, result.getStatus());
        assertEquals(conflictingAt, result.getUpdatedAt());
        verify(assertions).save(any(FactAssertion.class));
    }

    @Test
    void companyThatIsNotEventSubjectIsRejectedWithoutPartialState() {
        when(eventEntities.findById(eventSubject.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(input(new BigDecimal("100.00"), "table:revenue")));

        verify(facts, never()).findByDedupKey(any());
        verify(facts, never()).saveAndFlush(any());
        verify(assertions, never()).save(any());
    }

    @Test
    void evidenceNotLinkedToEventIsRejectedWithoutPartialState() {
        when(eventEvidence.findById(supportingLink.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(input(new BigDecimal("100.00"), "table:revenue")));

        verify(facts, never()).findByDedupKey(any());
        verify(facts, never()).saveAndFlush(any());
        verify(assertions, never()).save(any());
    }

    private Fact existingFact(BigDecimal value) throws Exception {
        byte[] key = EarningsFactDedupKey.create(FactPredicate.REVENUE,
                subject.getCanonicalKey(), PERIOD_START, PERIOD_END, "KRW");
        Fact fact = Fact.supportedNumber(subject, event, FactPredicate.REVENUE, value, "KRW",
                PERIOD_START, PERIOD_END, key, INITIAL_TIME);
        set(fact, "id", UUID.randomUUID());
        return fact;
    }

    private EarningsFactNormalizationInput input(BigDecimal value, String locator) {
        return new EarningsFactNormalizationInput(event.getId(), subject.getId(), evidence.getId(),
                FactPredicate.REVENUE, value, "KRW", PERIOD_START, PERIOD_END, locator);
    }

    private static <T> T newInstance(Class<T> type) throws Exception {
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
