package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.query.OfficialEvidenceQuery;
import com.aira.api.market.query.RealGdpIndicatorQuery;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named="DB_PASSWORD", matches=".+")
class RealGdpIndicatorQueryPostgresTests {
    @Autowired EcosRealGdpIngestionOperation operation;
    @Autowired RealGdpIndicatorQuery query;
    @Autowired OfficialEvidenceQuery evidenceQuery;
    @MockitoBean EcosRealGdpStatisticSearchClient client;

    @Test void latestReturnsTwoExactOfficialObservationsWithSharedEvidence() {
        when(client.fetch(any())).thenReturn(
                new EcosStatisticSearchPage(2, List.of(
                        row("2098Q1", "614115.3"), row("2098Q2", "617220.6"))),
                new EcosStatisticSearchPage(2, List.of(
                        row("2098Q2", "617220.6"), row("2098Q3", "619000.1"))));
        operation.ingest(new EcosRealGdpObservationScope("2098Q1", "2098Q2",
                new EcosObservationBudget(2, 1, 3)), OffsetDateTime.parse("2098-07-01T00:00:00Z"));
        operation.ingest(new EcosRealGdpObservationScope("2098Q2", "2098Q3",
                new EcosObservationBudget(2, 1, 3)), OffsetDateTime.parse("2098-10-01T00:00:00Z"));

        var result = query.latest();
        assertEquals(CanonicalDataState.AVAILABLE, result.state());
        assertEquals("십억원", result.unitName());
        assertEquals(List.of("2098Q3", "2098Q2"),
                result.observations().stream().map(o -> o.period()).toList());
        assertEquals(new BigDecimal("619000.1"), result.observations().getFirst().value());
        assertEquals(result.observations().getFirst().evidenceId(),
                result.observations().getLast().evidenceId());
        assertEquals("Bank of Korea ECOS",
                evidenceQuery.find(result.observations().getFirst().evidenceId()).source().sourceName());
    }

    private static EcosStatisticSearchObservation row(String time, String value) {
        return new EcosStatisticSearchObservation(EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME, EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1, null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME, null, time, value, new BigDecimal(value));
    }
}
