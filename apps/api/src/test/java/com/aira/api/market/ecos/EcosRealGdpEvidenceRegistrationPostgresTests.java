package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
class EcosRealGdpEvidenceRegistrationPostgresTests {
    @Autowired EcosRealGdpEvidenceRegistrationService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void identicalSnapshotReusesEvidenceAndKeepsFirstCollectionTime() {
        OffsetDateTime first = OffsetDateTime.parse("2026-09-12T00:00:00Z");
        OffsetDateTime second = first.plusHours(1);

        UUID one = service.register(result("596692.8"), first);
        UUID two = service.register(result("596692.8"), second);

        assertEquals(one, two);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM evidence e
                JOIN source s ON s.id=e.source_id
                WHERE s.source_type='GOVERNMENT' AND s.external_key='BOK_ECOS'
                  AND e.external_id=? AND e.revision=1
                """, Integer.class, externalId()));
        assertEquals(first.toInstant(), jdbc.queryForObject(
                "SELECT collected_at FROM evidence WHERE id=?",
                Timestamp.class, one).toInstant());
    }

    @Test
    void sameIdentityWithChangedProviderContentIsBlockedWithoutOverwrite() {
        OffsetDateTime collected = OffsetDateTime.parse("2026-09-12T00:00:00Z");
        UUID original = service.register(result("596692.8"), collected);
        byte[] originalHash = jdbc.queryForObject(
                "SELECT content_hash FROM evidence WHERE id=?", byte[].class, original);

        assertThrows(IllegalStateException.class,
                () -> service.register(result("596693.0"), collected.plusMinutes(1)));

        assertArrayEquals(originalHash, jdbc.queryForObject(
                "SELECT content_hash FROM evidence WHERE id=?", byte[].class, original));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM evidence
                WHERE external_id=? AND revision=1
                """, Integer.class, externalId()));
    }

    @Test
    void persistsFrozenSourceAndEvidenceContract() {
        OffsetDateTime collected = OffsetDateTime.parse("2026-09-12T00:00:00Z");
        UUID evidenceId = service.register(result("596692.8"), collected);

        var source = jdbc.queryForMap("""
                SELECT s.source_type, s.external_key, s.name, s.canonical_domain, s.active
                FROM source s JOIN evidence e ON e.source_id=s.id WHERE e.id=?
                """, evidenceId);
        assertEquals("GOVERNMENT", source.get("source_type"));
        assertEquals("BOK_ECOS", source.get("external_key"));
        assertEquals("Bank of Korea ECOS", source.get("name"));
        assertEquals("ecos.bok.or.kr", source.get("canonical_domain"));
        assertEquals(true, source.get("active"));
        var evidence = jdbc.queryForMap("""
                SELECT evidence_type, external_id, original_url, title, locator,
                       published_at, collected_at, revision, status, content_hash
                FROM evidence WHERE id=?
                """, evidenceId);
        assertEquals("OFFICIAL_DATA", evidence.get("evidence_type"));
        assertEquals(externalId(), evidence.get("external_id"));
        assertEquals("https://ecos.bok.or.kr/api/", evidence.get("original_url"));
        assertEquals("BOK ECOS official StatisticSearch: real GDP", evidence.get("title"));
        assertEquals("StatisticSearch/json/kr/200Y104/Q/2026Q1/2026Q1/1400/-/-/-",
                evidence.get("locator"));
        assertNull(evidence.get("published_at"));
        assertEquals(collected.toInstant(),
                ((Timestamp) evidence.get("collected_at")).toInstant());
        assertEquals(1, evidence.get("revision"));
        assertEquals("ACTIVE", evidence.get("status"));
        var expected = EcosRealGdpEvidenceSnapshotFactory.create(result("596692.8"), collected);
        assertArrayEquals(expected.contentHash(), (byte[]) evidence.get("content_hash"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM source
                WHERE source_type='GOVERNMENT' AND external_key='BOK_ECOS'
                """, Integer.class));
    }

    @Test
    void corruptedExistingSourceBlocksBeforeEvidenceInsert() {
        UUID sourceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO source(id, source_type, external_key, name, canonical_domain, active)
                VALUES (?, 'GOVERNMENT', 'BOK_ECOS', 'Wrong ECOS', 'wrong.example', true)
                """, sourceId);

        assertThrows(IllegalStateException.class, () -> service.register(
                result("596692.8"), OffsetDateTime.parse("2026-09-12T00:00:00Z")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM evidence WHERE source_id=?", Integer.class, sourceId));
    }

    private static String externalId() {
        return "BOK_ECOS:StatisticSearch:v1:kr:200Y104:Q:2026Q1:2026Q1:1400:-:-:-";
    }

    private static EcosRealGdpObservationResult result(String value) {
        var observation = new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                "2026Q1",
                value,
                new BigDecimal(value));
        return new EcosRealGdpObservationResult(
                "2026Q1", "2026Q1", 1, 1, 1, 2, List.of(observation));
    }
}
