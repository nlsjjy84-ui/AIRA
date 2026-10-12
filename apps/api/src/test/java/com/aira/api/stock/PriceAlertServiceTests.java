package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PriceAlertServiceTests {
    @Test
    void createsValidatesAndTriggersOnTheLatestCloseOnly() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:alerts-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        UUID companyId = UUID.randomUUID();
        var company = new StockCompanyLookup.Company(companyId, "삼성전자", "00126380");
        StockCompanyLookup lookup = mock(StockCompanyLookup.class);
        when(lookup.find(companyId)).thenReturn(Optional.of(company));
        StockPageService pages = mock(StockPageService.class);
        when(pages.profile("00126380")).thenReturn(Map.of("stock_code", "005930"));
        when(pages.shortCode(any(), any())).thenReturn("005930");
        var service = new PriceAlertService(jdbc, lookup, pages);
        UUID user = UUID.randomUUID();
        assertEquals("INVALID_DIRECTION", service.create(user, companyId, "SIDEWAYS", BigDecimal.TEN));
        assertEquals("INVALID_TARGET", service.create(user, companyId, "ABOVE", BigDecimal.ZERO));
        assertEquals("UNKNOWN_COMPANY", service.create(user, UUID.randomUUID(), "ABOVE", BigDecimal.TEN));
        assertNull(service.create(user, companyId, "ABOVE", new BigDecimal("270000")));
        assertNull(service.create(user, companyId, "BELOW", new BigDecimal("250000")));
        var prices = new StockPriceStore(jdbc);
        LocalDate today = LocalDate.now();
        prices.saveDay("KOSPI", today.minusDays(1), List.of(new StockPriceStore.Row("005930", today.minusDays(1), "KOSPI", new BigDecimal("280000"),
                null, null, null, null, null, null, null, null)));
        prices.saveDay("KOSPI", today, List.of(new StockPriceStore.Row("005930", today, "KOSPI", new BigDecimal("265000"),
                null, null, null, null, null, null, null, null)));
        assertEquals(0, service.evaluate(), "only the latest close counts and 265000 reaches neither target");
        prices.saveDay("KOSPI", today, List.of(new StockPriceStore.Row("005930", today, "KOSPI", new BigDecimal("249000"),
                null, null, null, null, null, null, null, null)));
        assertEquals(1, service.evaluate());
        assertEquals(0, service.evaluate(), "an alert rings once");
        var list = service.list(user);
        assertEquals(2, list.size());
        var rung = list.stream().filter(m -> m.get("triggeredAt") != null).findFirst().orElseThrow();
        assertEquals("BELOW", rung.get("direction"));
        assertNotNull(rung.get("triggeredClose"));
        assertEquals(true, service.delete(user, (UUID) rung.get("id")));
        assertEquals(false, service.delete(UUID.randomUUID(), (UUID) list.get(0).get("id")));
    }
}
