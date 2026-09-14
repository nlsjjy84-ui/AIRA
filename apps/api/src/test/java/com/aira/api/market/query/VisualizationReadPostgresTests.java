package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.FinancialExactComparisonResponse.ExactPeriod;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.krx.*;
import com.aira.api.market.opendart.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class VisualizationReadPostgresTests {
    @Autowired OpenDartFilingPersistence dart;
    @Autowired KrxPersistence krx;
    @Autowired FinancialExactComparisonQuery financial;
    @Autowired KrxStoredSeriesQuery market;
    @Autowired JdbcTemplate jdbc;

    @Test void verifiedExactAAndBOrNothingIncludingZeroBase() throws Exception {
        String corp = String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        String aReceipt = receipt(), bReceipt = receipt();
        var a = filing(corp, 2024, aReceipt, "0");
        var b = filing(corp, 2025, bReceipt, "125");
        var directory = new OpenDartCompanyDirectoryRecord(corp, "Comparison fixture", null,
                "000009", LocalDate.of(2026, 1, 1));
        UUID company = dart.persist(a, directory).companyId();
        assertEquals(company, dart.persist(b, directory).companyId());
        var periodA = new ExactPeriod(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 9, 30), aReceipt);
        var periodB = new ExactPeriod(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 9, 30), bReceipt);
        var answer = financial.find(company, periodA, periodB, Set.of(FactPredicate.REVENUE));
        assertEquals(CanonicalDataState.AVAILABLE, answer.state());
        assertEquals(new BigDecimal("125"), answer.metrics().getFirst().changeAmountBMinusA());
        assertNull(answer.metrics().getFirst().changePercentBOverA());
        assertEquals("BASE_NON_POSITIVE", answer.metrics().getFirst().percentReason());
        assertEquals(CanonicalDataState.NO_DATA, financial.find(company, periodA,
                new ExactPeriod(periodB.periodStart(), periodB.periodEnd(), "99999999999999"),
                Set.of(FactPredicate.REVENUE)).state());
    }

    @Test void officialStoredSeriesAndPreviousNeverSubstituteMissingD() {
        String suffix = String.format("%06d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L));
        String standard = "KR7VIZ" + suffix;
        String shortCode = suffix;
        int fixtureYear = 2100 + Math.floorMod(UUID.randomUUID().hashCode(), 7000);
        LocalDate a = LocalDate.of(fixtureYear, 1, 6), d = LocalDate.of(fixtureYear, 1, 8);
        stock(standard, shortCode, a, "100", "10");
        stock(standard, shortCode, d, "125", "20");
        UUID security = jdbc.queryForObject("""
                SELECT entity_id FROM entity_external_identifier
                WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value=?
                """, UUID.class, standard);
        var series = market.find(security, FactPredicate.CLOSE_PRICE, a, d);
        assertEquals(CanonicalDataState.AVAILABLE, series.state());
        assertEquals(List.of(a, d), series.points().stream().map(point -> point.tradingDate()).toList());
        assertTrue(series.points().stream().allMatch(point -> !point.evidenceIds().isEmpty()));
        var comparison = market.previous(security, FactPredicate.CLOSE_PRICE, d,
                series.points().getLast().factId());
        assertEquals(CanonicalDataState.AVAILABLE, comparison.state());
        assertEquals(a, comparison.previous().tradingDate());
        assertEquals(d, comparison.current().tradingDate());
        assertEquals(new BigDecimal("25"), comparison.changeAmount());
        assertEquals(new BigDecimal("25.0000"), comparison.changePercent());
        assertEquals(CanonicalDataState.NO_DATA, market.previous(security, FactPredicate.CLOSE_PRICE,
                d.plusDays(1), series.points().getLast().factId()).state());
        assertEquals(CanonicalDataState.NO_DATA, market.find(security, FactPredicate.OPEN_PRICE, a, d).state());
    }

    private OpenDartPreparedFiling filing(String corp, int year, String receipt, String value) throws Exception {
        var context = new OpenDartAnnualCfsContext(corp, year, "11011", "CFS");
        var period = OpenDartPeriodWitnessResolver.resolve(context, receipt,
                new OpenDartPeriodWitnessResponse("000", List.of(new OpenDartPeriodWitnessResponse.Row(
                        corp, Integer.toString(year), "11011", receipt, "CFS",
                        year + ".04.01 ~ " + year + ".09.30"))), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(receipt.getBytes(StandardCharsets.UTF_8));
        var evidence = new EvidenceRegistration(EvidenceType.DISCLOSURE, receipt,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt,
                "OpenDART annual CFS filing", hash, null, null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), 1);
        return new OpenDartPreparedFiling(context, evidence, period,
                List.of(new OpenDartPreparedFiling.Metric(FactPredicate.REVENUE,
                        new BigDecimal(value), "KRW", "CFS/IS/ifrs_Revenue/thstrm_amount")));
    }

    private void stock(String standard, String shortCode, LocalDate date, String close, String volume) {
        String basDd = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, date, List.of(Map.of(
                "ISU_CD", standard, "ISU_SRT_CD", shortCode, "ISU_NM", "Visualization fixture")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, date, List.of(Map.of(
                "BAS_DD", basDd, "ISU_CD", shortCode, "TDD_CLSPRC", close, "ACC_TRDVOL", volume)));
        krx.stock(KrxPreparedPacket.stock(base, daily));
    }

    private static String receipt() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),
                100_000_000_000_000L));
    }
}
