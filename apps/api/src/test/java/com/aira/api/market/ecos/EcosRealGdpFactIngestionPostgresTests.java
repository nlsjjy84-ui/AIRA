package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
class EcosRealGdpFactIngestionPostgresTests {
    @Autowired EcosRealGdpFactIngestionService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsExactQuarterFactsAndRepeatedIngestionIsIdempotent() {
        OffsetDateTime collectedAt = OffsetDateTime.parse("2031-07-01T00:00:00Z");
        var input = result(
                "2031Q1", "2031Q2",
                observation("2031Q1", "510000.0"),
                observation("2031Q2", "520000.0"));

        var first = service.ingest(input, collectedAt);
        var second = service.ingest(input, collectedAt);

        assertEquals(first, second);
        assertEquals(2, first.factIds().size());
        assertEquals(2, count("SELECT count(*) FROM fact WHERE id IN (?,?)",
                first.factIds().get(0), first.factIds().get(1)));
        assertEquals(2, count("SELECT count(*) FROM fact_statistical_context WHERE fact_id IN (?,?)",
                first.factIds().get(0), first.factIds().get(1)));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion WHERE evidence_id=?",
                first.evidenceId()));

        assertEquals(LocalDate.of(2031, 1, 1), jdbc.queryForObject(
                "SELECT period_start FROM fact WHERE id=?", LocalDate.class, first.factIds().get(0)));
        assertEquals(LocalDate.of(2031, 6, 30), jdbc.queryForObject(
                "SELECT period_end FROM fact WHERE id=?", LocalDate.class, first.factIds().get(1)));
        assertEquals(2, count("SELECT count(*) FROM fact WHERE id IN (?,?) AND event_id IS NULL AND currency_code IS NULL AND status='SUPPORTED'",
                first.factIds().get(0), first.factIds().get(1)));
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }

    @Test
    void overlappingEvidencePreservesSameFactAndConflictsOnChangedValue() {
        OffsetDateTime collectedAt = OffsetDateTime.parse("2032-10-01T00:00:00Z");
        var first = service.ingest(
                result("2032Q2", "2032Q2", observation("2032Q2", "530000.0")),
                collectedAt);
        var sameValue = service.ingest(
                result("2032Q1", "2032Q2", observation("2032Q2", "530000.00")),
                collectedAt.plusMinutes(1));

        assertEquals(first.factIds().getFirst(), sameValue.factIds().getFirst());
        assertNotEquals(first.evidenceId(), sameValue.evidenceId());
        assertEquals("SUPPORTED", jdbc.queryForObject(
                "SELECT status FROM fact WHERE id=?", String.class, first.factIds().getFirst()));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?",
                first.factIds().getFirst()));

        var changed = service.ingest(
                result("2032Q2", "2032Q3", observation("2032Q2", "531000.0")),
                collectedAt.plusMinutes(2));
        assertEquals(first.factIds().getFirst(), changed.factIds().getFirst());
        assertEquals("CONFLICTING", jdbc.queryForObject(
                "SELECT status FROM fact WHERE id=?", String.class, first.factIds().getFirst()));
        assertEquals(0, count("SELECT count(*) FROM fact WHERE id=? AND value_number IS NOT NULL",
                first.factIds().getFirst()));
        assertEquals(3, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?",
                first.factIds().getFirst()));
        assertEquals(1, count("SELECT count(*) FROM fact_statistical_context WHERE fact_id=?",
                first.factIds().getFirst()));
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }

    @Test
    void blankNumericObservationBlocksBeforePersistence() {
        int before = count("SELECT count(*) FROM fact WHERE predicate='REAL_GDP' AND period_start=DATE '2099-10-01'");
        var input = result(
                "2099Q4", "2099Q4",
                observationWithoutNumeric("2099Q4"));

        assertThrows(IllegalStateException.class,
                () -> service.ingest(input, OffsetDateTime.parse("2099-12-31T00:00:00Z")));

        assertEquals(before, count(
                "SELECT count(*) FROM fact WHERE predicate='REAL_GDP' AND period_start=DATE '2099-10-01'"));
    }

    private static EcosRealGdpObservationResult result(
            String start,
            String end,
            EcosStatisticSearchObservation... observations) {
        return new EcosRealGdpObservationResult(
                start,
                end,
                quarterCount(start, end),
                observations.length,
                1,
                3,
                List.of(observations));
    }

    private static EcosStatisticSearchObservation observation(String time, String value) {
        return new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                time,
                value,
                new BigDecimal(value));
    }

    private static EcosStatisticSearchObservation observationWithoutNumeric(String time) {
        return new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                time,
                "",
                null);
    }

    private static long quarterCount(String start, String end) {
        long startOrdinal = Long.parseLong(start.substring(0, 4)) * 4L + (start.charAt(5) - '0');
        long endOrdinal = Long.parseLong(end.substring(0, 4)) * 4L + (end.charAt(5) - '0');
        return endOrdinal - startOrdinal + 1L;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
