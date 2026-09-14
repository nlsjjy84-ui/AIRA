package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EcosRealGdpSearchRequestTests {
    @Test
    void acceptsExactQuarterRange() {
        var request = new EcosRealGdpSearchRequest(1, 1000, "2025Q4", "2026Q2");
        assertEquals("2025Q4", request.startTime());
        assertEquals("2026Q2", request.endTime());
    }

    @Test
    void rejectsInvalidRowsQuarterGrammarAndReversePeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSearchRequest(0, 1, "2026Q1", "2026Q2"));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSearchRequest(1, 1, "2026-01", "2026Q2"));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSearchRequest(1, 1, "2026Q3", "2026Q2"));
    }

    @Test
    void descriptorHasNoCredentialOrUrlField() {
        var names = Arrays.stream(EcosRealGdpSearchRequest.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .toList();
        assertFalse(names.stream().anyMatch(name ->
                name.contains("key") || name.contains("url")
                        || name.contains("uri") || name.contains("auth")));
    }
}
