package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EcosQuarterPeriodTests {
    @Test
    void mapsProviderQuarterToExactCalendarPeriod() {
        var q1 = EcosQuarterPeriod.from("2026Q1");
        var q4 = EcosQuarterPeriod.from("2026Q4");

        assertEquals(LocalDate.of(2026, 1, 1), q1.start());
        assertEquals(LocalDate.of(2026, 3, 31), q1.end());
        assertEquals(LocalDate.of(2026, 10, 1), q4.start());
        assertEquals(LocalDate.of(2026, 12, 31), q4.end());
    }

    @Test
    void rejectsNonQuarterProviderTime() {
        assertThrows(IllegalArgumentException.class, () -> EcosQuarterPeriod.from("2026M01"));
        assertThrows(IllegalArgumentException.class, () -> EcosQuarterPeriod.from("2026Q5"));
    }
}
