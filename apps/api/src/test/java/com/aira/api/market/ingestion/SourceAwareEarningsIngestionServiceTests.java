package com.aira.api.market.ingestion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.normalization.EarningsEventNormalizationService;
import com.aira.api.market.normalization.EarningsFactNormalizationInput;
import com.aira.api.market.normalization.EarningsFactNormalizationService;
import com.aira.api.market.normalization.EarningsNormalizationInput;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.service.SourceRegistration;
import com.aira.api.market.service.SourceRegistryService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SourceAwareEarningsIngestionServiceTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-19T00:00:00Z");

    @Mock private SourceRegistryService sources;
    @Mock private EvidenceRepository evidence;
    @Mock private EarningsEventNormalizationService events;
    @Mock private EarningsFactNormalizationService facts;

    private SourceAwareEarningsIngestionService service;
    private Source source;
    private Event event;
    private Fact fact;

    @BeforeEach
    void setUp() throws Exception {
        service = new SourceAwareEarningsIngestionService(sources, evidence, events, facts);
        source = instance(Source.class, UUID.randomUUID());
        event = instance(Event.class, UUID.randomUUID());
        fact = instance(Fact.class, UUID.randomUUID());
        lenient().when(sources.registerOrReuse(any())).thenReturn(source);
        lenient().when(evidence.saveAndFlush(any())).thenAnswer(invocation -> {
            Evidence saved = invocation.getArgument(0);
            set(saved, "id", UUID.randomUUID());
            return saved;
        });
        lenient().when(events.normalize(any())).thenReturn(event);
        lenient().when(facts.normalize(any())).thenReturn(fact);
    }

    @Test
    void ingestsThroughSourceEvidenceEventAndFactServices() {
        SourceAwareIngestionResult result = service.ingest(input("sec", "filing-1"));

        assertSame(source, result.source());
        assertSame(source, result.evidence().getSource());
        assertSame(event, result.event());
        assertSame(fact, result.fact());
        ArgumentCaptor<EarningsNormalizationInput> eventInput =
                ArgumentCaptor.forClass(EarningsNormalizationInput.class);
        verify(events).normalize(eventInput.capture());
        ArgumentCaptor<EarningsFactNormalizationInput> factInput =
                ArgumentCaptor.forClass(EarningsFactNormalizationInput.class);
        verify(facts).normalize(factInput.capture());
        assertSame(result.evidence().getId(), eventInput.getValue().evidenceId());
        assertSame(result.evidence().getId(), factInput.getValue().evidenceId());
        assertSame(event.getId(), factInput.getValue().eventId());
    }

    @Test
    void repeatedSourceRegistrationUsesRegistryForEveryIngestion() {
        service.ingest(input("sec", "filing-1"));
        service.ingest(input("sec", "filing-2"));

        verify(sources, org.mockito.Mockito.times(2)).registerOrReuse(any());
        verify(evidence, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test
    void differentSourcesAreDelegatedWithTheirOwnStableIdentity() {
        service.ingest(input("sec", "filing-1"));
        service.ingest(input("fca", "filing-2"));

        ArgumentCaptor<SourceRegistration> registrations =
                ArgumentCaptor.forClass(SourceRegistration.class);
        verify(sources, org.mockito.Mockito.times(2)).registerOrReuse(registrations.capture());
        org.junit.jupiter.api.Assertions.assertEquals("sec", registrations.getAllValues().get(0).externalKey());
        org.junit.jupiter.api.Assertions.assertEquals("fca", registrations.getAllValues().get(1).externalKey());
    }

    @Test
    void invalidSourceIsRejectedByExistingRegistryInputRules() {
        assertThrows(IllegalArgumentException.class, () -> new SourceRegistration(
                SourceType.REGULATOR, " ", "Regulator", "example.gov"));
        verify(sources, never()).registerOrReuse(any());
    }

    @Test
    void evidenceHashIsDefensivelyCopied() {
        byte[] hash = new byte[] {1, 2, 3};
        EvidenceRegistration registration = evidence("filing-1", hash);
        hash[0] = 9;
        byte[] returned = registration.contentHash();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, registration.contentHash());
    }

    @Test
    void nullInputCreatesNoPartialState() {
        assertThrows(IllegalArgumentException.class, () -> service.ingest(null));
        verify(sources, never()).registerOrReuse(any());
        verify(evidence, never()).saveAndFlush(any());
        verify(events, never()).normalize(any());
        verify(facts, never()).normalize(any());
    }

    @Test
    void ingestionMethodDefinesSingleTransactionBoundary() throws Exception {
        Transactional annotation = SourceAwareEarningsIngestionService.class
                .getMethod("ingest", SourceAwareEarningsIngestionInput.class)
                .getAnnotation(Transactional.class);
        org.junit.jupiter.api.Assertions.assertNotNull(annotation);
    }

    private SourceAwareEarningsIngestionInput input(String sourceKey, String evidenceId) {
        return new SourceAwareEarningsIngestionInput(
                new SourceRegistration(SourceType.REGULATOR, sourceKey, "Regulator", "example.gov"),
                evidence(evidenceId, new byte[] {1, 2, 3}), UUID.randomUUID(),
                LocalDate.of(2026, 6, 30), "2026 Q2 results", NOW,
                FactPredicate.REVENUE, new BigDecimal("100.00"), "USD",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "table:revenue");
    }

    private EvidenceRegistration evidence(String externalId, byte[] hash) {
        return new EvidenceRegistration(EvidenceType.DISCLOSURE, externalId,
                "https://example.gov/" + externalId, "Filing", hash,
                "table:revenue", NOW, NOW, 1);
    }

    private static <T> T instance(Class<T> type, UUID id) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        T value = constructor.newInstance();
        set(value, "id", id);
        return value;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
