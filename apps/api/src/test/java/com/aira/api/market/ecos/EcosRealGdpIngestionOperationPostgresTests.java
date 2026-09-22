package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named="DB_PASSWORD", matches=".+")
class EcosRealGdpIngestionOperationPostgresTests {
    @Autowired EcosRealGdpIngestionOperation operation;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EcosRealGdpStatisticSearchClient client;
    private final OffsetDateTime collected = OffsetDateTime.parse("2041-07-01T00:00:00Z");

    @Test void exactQuartersShareEvidenceAndReplayIsIdempotentOutsideNetworkTransaction() {
        when(client.fetch(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new EcosStatisticSearchPage(2,List.of(row("2041Q1","610000"),row("2041Q2","620000")));
        });
        var scope = scope("2041Q1","2041Q2");
        var first = operation.ingest(scope,collected);
        var before = counts();
        assertEquals(first,operation.ingest(scope,collected.plusSeconds(1)));
        assertEquals(before,counts());
        assertEquals(2,first.factIds().size());
        assertEquals(2,jdbc.queryForObject("select count(*) from fact_assertion where evidence_id=?",Integer.class,first.evidenceId()));
        assertEquals(java.time.LocalDate.of(2041,1,1),jdbc.queryForObject("select period_start from fact where id=?",java.time.LocalDate.class,first.factIds().getFirst()));
        assertEquals(java.time.LocalDate.of(2041,6,30),jdbc.queryForObject("select period_end from fact where id=?",java.time.LocalDate.class,first.factIds().getLast()));
        assertEquals(0,jdbc.queryForObject("select count(*) from fact where id in (?,?) and event_id is not null",Integer.class,first.factIds().getFirst(),first.factIds().getLast()));
    }
    @Test void readFailureWritesNothing() {
        var before=counts();
        when(client.fetch(any())).thenThrow(new IllegalStateException("controlled provider failure"));
        assertThrows(IllegalStateException.class,()->operation.ingest(scope("2042Q1","2042Q1"),collected));
        assertEquals(before,counts());
    }
    @Test void databaseFailureOnSecondQuarterRollsBackFirstQuarterAndEvidence() {
        var before=counts();
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION aira_test_fail_ecos_q2() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'controlled second-quarter failure'; END;
            $$ LANGUAGE plpgsql
            """);
        jdbc.execute("""
            CREATE TRIGGER aira_test_fail_ecos_q2
            BEFORE INSERT ON fact
            FOR EACH ROW WHEN (NEW.predicate='REAL_GDP' AND NEW.period_start=DATE '2043-04-01')
            EXECUTE FUNCTION aira_test_fail_ecos_q2()
            """);
        try {
            when(client.fetch(any())).thenReturn(new EcosStatisticSearchPage(2,
                    List.of(row("2043Q1","630000"),row("2043Q2","640000"))));
            assertThrows(RuntimeException.class,
                    () -> operation.ingest(scope("2043Q1","2043Q2"),collected));
            assertEquals(before,counts());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS aira_test_fail_ecos_q2 ON fact");
            jdbc.execute("DROP FUNCTION IF EXISTS aira_test_fail_ecos_q2()");
        }
    }
    private List<Long> counts() {
        return List.of("source","entity","evidence","statistical_series","fact","fact_assertion",
                "fact_statistical_context","event","assessment")
            .stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList();
    }
    private static EcosRealGdpObservationScope scope(String start,String end) {
        return new EcosRealGdpObservationScope(start,end,new EcosObservationBudget(2,1,3));
    }
    private static EcosStatisticSearchObservation row(String time,String value) {
        return new EcosStatisticSearchObservation(EcosRealGdpContract.STAT_CODE,EcosRealGdpContract.STAT_NAME,
            EcosRealGdpContract.ITEM_CODE1,EcosRealGdpContract.ITEM_NAME1,null,null,null,null,null,null,
            EcosRealGdpContract.UNIT_NAME,null,time,value,value.isBlank()?null:new BigDecimal(value));
    }
}
