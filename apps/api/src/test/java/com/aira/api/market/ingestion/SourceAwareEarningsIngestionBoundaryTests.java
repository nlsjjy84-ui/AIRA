package com.aira.api.market.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.Source;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SourceAwareEarningsIngestionBoundaryTests {
    @Mock private SourceAwareEarningsIngestionService ingestion;
    @Mock private SourceAwareEarningsIngestionInput input;

    private EarningsIngestionBoundary boundary;

    @BeforeEach
    void setUp() {
        boundary = new SourceAwareEarningsIngestionBoundary(ingestion);
    }

    @Test
    void adapterCallerUsesBoundaryAndReceivesOnlyStableIdentifiers() throws Exception {
        Source source = instance(Source.class);
        Evidence evidence = instance(Evidence.class);
        Event event = instance(Event.class);
        Fact fact = instance(Fact.class);
        when(ingestion.ingest(input)).thenReturn(
                new SourceAwareIngestionResult(source, evidence, event, fact));
        FakeProviderAdapter adapter = new FakeProviderAdapter(boundary);

        IngestionReceipt receipt = adapter.submit(input);

        assertEquals(source.getId(), receipt.sourceId());
        assertEquals(evidence.getId(), receipt.evidenceId());
        assertEquals(event.getId(), receipt.eventId());
        assertEquals(fact.getId(), receipt.factId());
        verify(ingestion).ingest(input);
    }

    @Test
    void inputIsPassedThroughWithoutRebuildingPersistenceObjects() throws Exception {
        SourceAwareIngestionResult result = new SourceAwareIngestionResult(
                instance(Source.class), instance(Evidence.class),
                instance(Event.class), instance(Fact.class));
        when(ingestion.ingest(input)).thenReturn(result);

        boundary.ingest(input);

        verify(ingestion).ingest(input);
    }

    @Test
    void existingValidationFailureCrossesBoundaryUnchanged() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid source");
        when(ingestion.ingest(input)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> boundary.ingest(input)));
    }

    @Test
    void missingDownstreamResultIsRejected() {
        when(ingestion.ingest(input)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> boundary.ingest(input));
    }

    @Test
    void boundaryContractDoesNotExposeJpaEntitiesOrRepositories() {
        var method = EarningsIngestionBoundary.class.getDeclaredMethods()[0];
        assertEquals(SourceAwareEarningsIngestionInput.class, method.getParameterTypes()[0]);
        assertEquals(IngestionReceipt.class, method.getReturnType());
        assertFalse(Arrays.stream(SourceAwareEarningsIngestionBoundary.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type.getName().contains("repository")
                        || type.getPackageName().endsWith("domain")));
    }

    @Test
    void boundaryDoesNotOwnASecondTransaction() throws Exception {
        assertFalse(SourceAwareEarningsIngestionBoundary.class
                .getMethod("ingest", SourceAwareEarningsIngestionInput.class)
                .isAnnotationPresent(Transactional.class));
        assertFalse(SourceAwareEarningsIngestionBoundary.class
                .isAnnotationPresent(Transactional.class));
    }

    private static <T> T instance(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        T value = constructor.newInstance();
        Field id = type.getDeclaredField("id");
        id.setAccessible(true);
        id.set(value, UUID.randomUUID());
        return value;
    }

    private static final class FakeProviderAdapter {
        private final EarningsIngestionBoundary boundary;

        private FakeProviderAdapter(EarningsIngestionBoundary boundary) {
            this.boundary = boundary;
        }

        private IngestionReceipt submit(SourceAwareEarningsIngestionInput command) {
            return boundary.ingest(command);
        }
    }
}
