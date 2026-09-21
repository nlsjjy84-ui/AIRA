package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.krx.KrxDataset;
import com.aira.api.market.krx.KrxPersistence;
import com.aira.api.market.krx.KrxSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class MarketIndexQueryPostgresTests {
    @Autowired KrxPersistence persistence;
    @Autowired MarketIndexQuery query;

    @Test void latestReturnsExactSupportedKrxIndexFactsWithEvidence() {
        LocalDate date = LocalDate.of(2099, 12, 30);
        persistence.index(snapshot(KrxDataset.KOSPI_INDEX, date, "KOSPI", "3123.45", "-12.30", "-0.39"));
        persistence.index(snapshot(KrxDataset.KOSDAQ_INDEX, date, "코스닥", "987.65", "+4.20", "+0.43"));

        var board = query.latest();
        assertEquals(2, board.indices().size());
        var kospi = board.indices().stream().filter(item -> item.marketCode().equals("KOSPI")).findFirst().orElseThrow();
        var kosdaq = board.indices().stream().filter(item -> item.marketCode().equals("KOSDAQ")).findFirst().orElseThrow();
        assertEquals(CanonicalDataState.AVAILABLE, kospi.state());
        assertEquals(date, kospi.tradingDate());
        assertEquals(new BigDecimal("3123.45"), kospi.close());
        assertEquals(new BigDecimal("-12.30"), kospi.change());
        assertEquals(new BigDecimal("-0.39"), kospi.changeRate());
        assertNotNull(kospi.evidenceId());
        assertEquals("KRX Data Marketplace Open API", kospi.sourceName());
        assertTrue(kospi.originalUrl().contains("kospi_dd_trd"));
        assertEquals(CanonicalDataState.AVAILABLE, kosdaq.state());
        assertEquals(new BigDecimal("0.43"), kosdaq.changeRate());
    }

    private static KrxSnapshot snapshot(KrxDataset dataset, LocalDate date, String name,
            String close, String change, String rate) {
        return KrxSnapshot.validated(dataset, date, List.of(Map.of(
                "BAS_DD", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                "IDX_CLSS", dataset.market(), "IDX_NM", name, "CLSPRC_IDX", close,
                "CMPPREVDD_IDX", change, "FLUC_RT", rate)));
    }
}
