package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EcosPageRangeTests {
    @Test void acceptsValidRange() {
        assertEquals(new EcosPageRange(1, 1000), new EcosPageRange(1, 1000));
    }

    @Test void rejectsStartBeforeOne() {
        assertThrows(IllegalArgumentException.class, () -> new EcosPageRange(0, 1));
    }

    @Test void rejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class, () -> new EcosPageRange(2, 1));
    }
}
