package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventTests {
    private static final OffsetDateTime INITIAL_OBSERVED_AT =
            OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime INITIAL_UPDATED_AT =
            OffsetDateTime.parse("2026-01-01T01:00:00Z");

    @Test
    void unknownOccurrenceIsNotReplacedByObservationTime() {
        Event event = Event.createEarnings("2025 fiscal year", null,
                INITIAL_OBSERVED_AT, new byte[32], INITIAL_UPDATED_AT);
        org.junit.jupiter.api.Assertions.assertNull(event.getOccurredAt());
        event.confirm(true, true, INITIAL_UPDATED_AT);
        assertEquals(EventStatus.CONFIRMED, event.getStatus());
    }

    @Test
    void laterObservationUpdatesObservationAndProcessingTimes() {
        Event event = event();
        OffsetDateTime observedAt = INITIAL_OBSERVED_AT.plusDays(1);
        OffsetDateTime now = INITIAL_UPDATED_AT.plusDays(2);

        event.observeAt(observedAt, now);

        assertEquals(observedAt, event.getLastObservedAt());
        assertEquals(now, event.getUpdatedAt());
    }

    @Test
    void sameObservationKeepsObservationAndProcessingTimes() {
        Event event = event();

        event.observeAt(INITIAL_OBSERVED_AT, INITIAL_UPDATED_AT.plusDays(1));

        assertEquals(INITIAL_OBSERVED_AT, event.getLastObservedAt());
        assertEquals(INITIAL_UPDATED_AT, event.getUpdatedAt());
    }

    @Test
    void olderObservationKeepsObservationAndProcessingTimes() {
        Event event = event();

        event.observeAt(INITIAL_OBSERVED_AT.minusDays(1), INITIAL_UPDATED_AT.plusDays(1));

        assertEquals(INITIAL_OBSERVED_AT, event.getLastObservedAt());
        assertEquals(INITIAL_UPDATED_AT, event.getUpdatedAt());
    }

    @Test
    void mergedAndDiscardedEventsRejectObservation() throws Exception {
        for (EventStatus status : new EventStatus[] {EventStatus.MERGED, EventStatus.DISCARDED}) {
            Event event = event();
            setStatus(event, status);

            assertThrows(IllegalStateException.class, () -> event.observeAt(
                    INITIAL_OBSERVED_AT.plusDays(1), INITIAL_UPDATED_AT.plusDays(1)));
            assertEquals(INITIAL_OBSERVED_AT, event.getLastObservedAt());
            assertEquals(INITIAL_UPDATED_AT, event.getUpdatedAt());
        }
    }

    @Test
    void confirmsOnlyAfterEntityAndEvidenceAreReady() {
        Event event = event();
        OffsetDateTime confirmedAt = INITIAL_UPDATED_AT.plusDays(1);

        assertThrows(IllegalStateException.class,
                () -> event.confirm(false, true, confirmedAt));
        assertThrows(IllegalStateException.class,
                () -> event.confirm(true, false, confirmedAt));

        event.confirm(true, true, confirmedAt);

        assertEquals(EventStatus.CONFIRMED, event.getStatus());
        assertEquals(confirmedAt, event.getUpdatedAt());
    }

    @Test
    void confirmingAnAlreadyConfirmedEventIsIdempotent() {
        Event event = event();
        event.confirm(true, true, INITIAL_UPDATED_AT.plusDays(1));

        event.confirm(true, true, INITIAL_UPDATED_AT.plusDays(2));

        assertEquals(INITIAL_UPDATED_AT.plusDays(1), event.getUpdatedAt());
    }

    private static Event event() {
        return Event.createEarnings("Neutral earnings title", INITIAL_OBSERVED_AT,
                INITIAL_OBSERVED_AT, new byte[32], INITIAL_UPDATED_AT);
    }

    private static void setStatus(Event event, EventStatus status) throws Exception {
        Field field = Event.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(event, status);
    }
}
