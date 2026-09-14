package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FactPeriodEvidenceTests {
    private final LocalDate start = LocalDate.of(2025, 4, 1);
    private final LocalDate end = LocalDate.of(2025, 9, 30);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    private Fact fact() {
        Fact fact = mock(Fact.class);
        when(fact.getId()).thenReturn(UUID.randomUUID());
        when(fact.getPeriodStart()).thenReturn(start);
        when(fact.getPeriodEnd()).thenReturn(end);
        return fact;
    }

    private Evidence evidence() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getId()).thenReturn(UUID.randomUUID());
        return evidence;
    }

    @Test
    void preservesExactShortPeriodLocationAndIdentityWithoutValueAssertion() {
        Fact fact = fact();
        Evidence evidence = evidence();
        var link = FactPeriodEvidence.verified(fact, evidence, "section 2 / period", start, end, now);
        assertSame(fact, link.getFact());
        assertSame(evidence, link.getEvidence());
        assertEquals(new FactPeriodEvidenceId(fact.getId(), evidence.getId()), link.getId());
        assertEquals("section 2 / period", link.getLocator());
        assertEquals(now, link.getCreatedAt());
        verify(fact, never()).getValueNumber();
    }

    @Test
    void rejectsAbsentReversedOrMismatchedPeriod() {
        Fact fact = fact();
        Evidence evidence = evidence();
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", null, end, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", start, null, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", end, start, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", start.minusDays(1), end, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", start, end.plusDays(1), now));
        when(fact.getPeriodStart()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", start, end, now));
    }

    @Test
    void rejectsMissingIdentifiersLocationsAndTimestamp() {
        Fact fact = fact();
        Evidence evidence = evidence();
        assertThrows(IllegalArgumentException.class, () -> new FactPeriodEvidenceId(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new FactPeriodEvidenceId(UUID.randomUUID(), null));
        for (String locator : new String[] {null, "", " ", "\t\n"}) {
            assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, locator, start, end, now));
        }
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(null, evidence, "p", start, end, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, null, "p", start, end, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, evidence, "p", start, end, null));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(mock(Fact.class), evidence, "p", start, end, now));
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(fact, mock(Evidence.class), "p", start, end, now));
    }
}
