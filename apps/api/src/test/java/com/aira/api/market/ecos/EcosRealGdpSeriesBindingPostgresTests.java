package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
class EcosRealGdpSeriesBindingPostgresTests {
    @Autowired EcosRealGdpSeriesBindingService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void repeatedBindingReusesOneExactKoreaRealGdpGraph() {
        var first = service.registerOrReuse();
        var second = service.registerOrReuse();

        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM entity
                WHERE id=? AND entity_type='COUNTRY'
                  AND canonical_key='COUNTRY:KR'
                  AND canonical_name='Republic of Korea'
                  AND country_code='KR' AND active=true
                """, Integer.class, first.countryEntityId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM source
                WHERE id=? AND source_type='GOVERNMENT'
                  AND external_key='BOK_ECOS'
                  AND name='Bank of Korea ECOS'
                  AND canonical_domain='ecos.bok.or.kr'
                  AND active=true
                """, Integer.class, first.sourceId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series
                WHERE id=? AND subject_entity_id=?
                  AND metric='REAL_GDP' AND frequency='QUARTERLY'
                  AND adjustment='SEASONALLY_ADJUSTED'
                  AND value_kind='LEVEL' AND active=true
                """, Integer.class,
                first.statisticalSeriesId(), first.countryEntityId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series_source_mapping
                WHERE id=? AND statistical_series_id=? AND source_id=?
                  AND provider_binding_key=?
                  AND provider_series_name=?
                  AND provider_item_name=?
                  AND provider_frequency_code='Q'
                  AND provider_unit_name='십억원'
                  AND metadata_locator=? AND active=true
                """, Integer.class,
                first.sourceMappingId(), first.statisticalSeriesId(), first.sourceId(),
                EcosRealGdpSeriesBindingService.PROVIDER_BINDING_KEY,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_NAME1,
                EcosRealGdpSeriesBindingService.METADATA_LOCATOR));
    }

    @Test
    void sourceDriftBlocksBeforeSeriesOrMappingWrite() {
        var existing = jdbc.query("""
                SELECT id FROM source
                WHERE source_type='GOVERNMENT' AND external_key='BOK_ECOS'
                """, (rs, row) -> rs.getObject(1, UUID.class));
        UUID sourceId;
        if (existing.isEmpty()) {
            sourceId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO source(
                        id,source_type,external_key,name,canonical_domain,active)
                    VALUES (?, 'GOVERNMENT','BOK_ECOS','Wrong ECOS','wrong.example',true)
                    """, sourceId);
        } else {
            sourceId = existing.getFirst();
            jdbc.update("""
                    UPDATE source SET name='Wrong ECOS', canonical_domain='wrong.example'
                    WHERE id=?
                    """, sourceId);
        }

        int seriesBefore = realGdpSeriesCount();
        int mappingsBefore = realGdpMappingCount();
        assertThrows(IllegalStateException.class, service::registerOrReuse);

        assertEquals("Wrong ECOS", jdbc.queryForObject(
                "SELECT name FROM source WHERE id=?", String.class, sourceId));
        assertEquals(seriesBefore, realGdpSeriesCount());
        assertEquals(mappingsBefore, realGdpMappingCount());
    }

    @Test
    void mappingMetadataDriftBlocksWithoutOverwrite() {
        var binding = service.registerOrReuse();
        jdbc.update("""
                UPDATE statistical_series_source_mapping
                SET provider_unit_name='억원'
                WHERE id=?
                """, binding.sourceMappingId());

        assertThrows(IllegalStateException.class, service::registerOrReuse);

        assertEquals("억원", jdbc.queryForObject("""
                SELECT provider_unit_name
                FROM statistical_series_source_mapping
                WHERE id=?
                """, String.class, binding.sourceMappingId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series_source_mapping
                WHERE source_id=? AND provider_binding_key=?
                """, Integer.class,
                binding.sourceId(), EcosRealGdpSeriesBindingService.PROVIDER_BINDING_KEY));
    }

    private int realGdpSeriesCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series ss
                JOIN entity e ON e.id=ss.subject_entity_id
                WHERE e.canonical_key='COUNTRY:KR'
                  AND ss.metric='REAL_GDP'
                  AND ss.frequency='QUARTERLY'
                  AND ss.adjustment='SEASONALLY_ADJUSTED'
                  AND ss.value_kind='LEVEL'
                """, Integer.class);
    }

    private int realGdpMappingCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series_source_mapping m
                JOIN source s ON s.id=m.source_id
                WHERE s.source_type='GOVERNMENT' AND s.external_key='BOK_ECOS'
                  AND m.provider_binding_key=?
                """, Integer.class,
                EcosRealGdpSeriesBindingService.PROVIDER_BINDING_KEY);
    }
}
