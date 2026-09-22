package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named="AIRA_ECOS_LIVE_VERIFY",matches="true")
class EcosRealGdpLiveVerificationTests {
    @Autowired EcosRealGdpIngestionOperation operation;
    @Autowired EcosRealGdpObservationReader reader;
    @Autowired JdbcTemplate jdbc;

    @Test void realQuartersPersistAndReplayWithExactProviderValues() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_ecos_live_test"));
        var scope=new EcosRealGdpObservationScope("2025Q1","2025Q2",new EcosObservationBudget(2,1,3));
        var expected=reader.read(scope);
        assertEquals(2,expected.observations().size());
        var first=operation.ingest(scope,OffsetDateTime.now());
        assertEquals(2,first.factIds().size());
        for(var observation:expected.observations()) {
            var period=EcosQuarterPeriod.from(observation.time());
            var stored=jdbc.queryForObject("select value_number from fact where predicate='REAL_GDP' and period_start=? and period_end=?",BigDecimal.class,period.start(),period.end());
            assertEquals(0,observation.numericValue().compareTo(stored));
        }
        var before=counts();
        assertEquals(first,operation.ingest(scope,OffsetDateTime.now()));
        assertEquals(before,counts());
        System.out.println("ECOS_LIVE_VERIFIED quarters=2025Q1,2025Q2 counts="+before);
    }
    private List<Long> counts() {
        return List.of("evidence","statistical_series","fact","fact_assertion","fact_statistical_context")
            .stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList();
    }
}
