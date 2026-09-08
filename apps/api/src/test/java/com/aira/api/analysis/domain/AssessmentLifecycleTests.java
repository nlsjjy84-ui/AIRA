package com.aira.api.analysis.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.domain.Event;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssessmentLifecycleTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    void completedAssessmentRequiresConfirmedEvent() throws Exception {
        Event candidate = event(false);

        assertThrows(IllegalStateException.class, () -> completed(candidate, null));
    }

    @Test
    void supersessionMustStayWithinOneEvent() throws Exception {
        Event firstEvent = event(true);
        Event otherEvent = event(true);
        Assessment predecessor = completed(firstEvent, null);
        set(predecessor, "id", UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> completed(otherEvent, predecessor));
    }

    @Test
    void completedAssessmentRetainsItsExactPredecessor() throws Exception {
        Event event = event(true);
        Assessment predecessor = completed(event, null);
        set(predecessor, "id", UUID.randomUUID());

        Assessment successor = completed(event, predecessor);

        assertSame(predecessor, successor.getSupersedesAssessment());
    }

    private static Assessment completed(Event event, Assessment predecessor) {
        return Assessment.completedRule(event, "rule-v1", Importance.MEDIUM, "summary",
                Confidence.MEDIUM, "uncertainty", TimeHorizon.UNSPECIFIED,
                new byte[] {1}, predecessor, NOW);
    }

    private static Event event(boolean confirmed) throws Exception {
        Event event = Event.createEarnings("title", NOW, NOW, new byte[] {1}, NOW);
        set(event, "id", UUID.randomUUID());
        if (confirmed) event.confirm(true, true, NOW);
        return event;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
