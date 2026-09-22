package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
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
@EnabledIfEnvironmentVariable(named="AIRA_KRX_LIVE_VERIFY", matches="true")
class KrxLiveVerificationTests {
    @Autowired HttpKrxClient client;
    @Autowired KrxPersistence persistence;
    @Autowired JdbcTemplate jdbc;

    @Test void sixLiveSnapshotsPersistExactValuesAndReplayWithoutDuplicates() {
        LocalDate date = LocalDate.parse(System.getenv("AIRA_KRX_VERIFY_DATE"));
        assertTrue(jdbc.queryForObject("select current_database()", String.class).endsWith("_krx_live_test"));
        var snapshots = new EnumMap<KrxDataset, KrxSnapshot>(KrxDataset.class);
        for (var dataset : KrxDataset.values()) {
            var snapshot = client.fetch(dataset, date);
            assertFalse(snapshot.rows().isEmpty(), dataset.apiId());
            snapshots.put(dataset, snapshot);
        }
        var packets = List.of(
            KrxPreparedPacket.stock(snapshots.get(KrxDataset.STK_BASE), snapshots.get(KrxDataset.STK_DAILY)),
            KrxPreparedPacket.stock(snapshots.get(KrxDataset.KSQ_BASE), snapshots.get(KrxDataset.KSQ_DAILY)));
        for (var packet : packets) persistence.stock(packet);
        for (var dataset : List.of(KrxDataset.KOSPI_INDEX, KrxDataset.KOSDAQ_INDEX))
            persistence.index(snapshots.get(dataset));
        for (var packet : packets) {
            String evidence = "KRX_OPENAPI:" + packet.daily().dataset().apiId() + ":" + packet.daily().basDd();
            var actual = new HashMap<String, BigDecimal>();
            jdbc.query("""
                select x.identifier_value, f.predicate, f.value_number
                from fact f join entity_external_identifier x on x.entity_id=f.subject_entity_id
                join fact_assertion a on a.fact_id=f.id join evidence e on e.id=a.evidence_id
                where x.namespace='KRX' and x.identifier_type='STANDARD_CODE' and e.external_id=?
                """, rs -> { actual.put(rs.getString(1)+"/"+rs.getString(2),rs.getBigDecimal(3)); }, evidence);
            assertEquals(packet.values().size(), actual.size());
            for (var value : packet.values()) {
                var stored = actual.get(value.standardCode()+"/"+value.predicate().name());
                assertNotNull(stored);
                assertEquals(0, value.value().compareTo(stored));
            }
        }
        var before = counts();
        for (var packet : packets) persistence.stock(packet);
        for (var dataset : List.of(KrxDataset.KOSPI_INDEX, KrxDataset.KOSDAQ_INDEX))
            persistence.index(snapshots.get(dataset));
        assertEquals(before, counts());
        assertEquals(6, jdbc.queryForObject("select count(*) from evidence where external_id like 'KRX_OPENAPI:%'", Integer.class));
        System.out.println("KRX_LIVE_VERIFIED date="+date+" rows="+snapshots.values().stream().mapToInt(s -> s.rows().size()).sum()+" counts="+before);
    }

    private List<Long> counts() {
        return List.of("source","evidence","entity","entity_external_identifier","fact","fact_assertion")
            .stream().map(table -> jdbc.queryForObject("select count(*) from "+table,Long.class)).toList();
    }
}
