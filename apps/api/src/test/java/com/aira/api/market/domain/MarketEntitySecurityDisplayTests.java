package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketEntitySecurityDisplayTests {
    @Test
    void refreshesOnlySecurityDisplayNameWithoutChangingIdentityMetadata() {
        OffsetDateTime created = OffsetDateTime.of(2035, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime refreshed = created.plusDays(1);
        var security = MarketEntity.security("broken-name", "KOSPI", "035420",
                UUID.randomUUID(), created);
        String canonicalKey = security.getCanonicalKey();

        security.refreshSecurityDisplayName("NAVER", refreshed);

        assertEquals("NAVER", security.getCanonicalName());
        assertEquals(canonicalKey, security.getCanonicalKey());
        assertEquals("KOSPI", security.getMarketCode());
        assertEquals("035420", security.getSymbol());
        assertEquals(refreshed, security.getUpdatedAt());
    }

    @Test
    void refusesDisplayRefreshForNonSecurityEntity() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var company = MarketEntity.company("NAVER", "KR", UUID.randomUUID(), now);
        assertThrows(IllegalArgumentException.class,
                () -> company.refreshSecurityDisplayName("NAVER", now.plusMinutes(1)));
    }
}
