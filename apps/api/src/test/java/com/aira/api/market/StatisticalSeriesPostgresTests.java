package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalValueKind;
import com.aira.api.market.service.KoreaCountryBootstrapOperation;
import com.aira.api.market.service.StatisticalSeriesRegistration;
import com.aira.api.market.service.StatisticalSeriesRegistryService;
import com.aira.api.market.service.StatisticalSeriesSourceMappingRegistration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
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
class StatisticalSeriesPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticalSeriesRegistryService service;
    @Autowired KoreaCountryBootstrapOperation countries;
    @Autowired DataSource dataSource;

    @Test
    void identicalSeriesAndMappingRegistrationsAreIdempotent() {
        UUID country = country();
        UUID source = source();
        var seriesRegistration = series(country);

        UUID firstSeries = service.registerSeries(seriesRegistration);
        UUID secondSeries = service.registerSeries(seriesRegistration);
        assertEquals(firstSeries, secondSeries);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series
                WHERE subject_entity_id=? AND metric='REAL_GDP'
                  AND frequency='QUARTERLY'
                  AND adjustment='SEASONALLY_ADJUSTED'
                  AND value_kind='LEVEL'
                """, Integer.class, country));

        var mappingRegistration = mapping(firstSeries, source, "십억원");
        UUID firstMapping = service.registerSourceMapping(mappingRegistration);
        UUID secondMapping = service.registerSourceMapping(mappingRegistration);
        assertEquals(firstMapping, secondMapping);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series_source_mapping
                WHERE source_id=? AND provider_binding_key=?
                """, Integer.class, source, providerBindingKey()));
    }

    @Test
    void changedProviderMetadataBlocksWithoutOverwrite() {
        UUID country = country();
        UUID source = source();
        UUID seriesId = service.registerSeries(series(country));
        service.registerSourceMapping(mapping(seriesId, source, "십억원"));

        assertThrows(IllegalStateException.class,
                () -> service.registerSourceMapping(mapping(seriesId, source, "억원")));

        assertEquals("십억원", jdbc.queryForObject("""
                SELECT provider_unit_name
                FROM statistical_series_source_mapping
                WHERE source_id=? AND provider_binding_key=?
                """, String.class, source, providerBindingKey()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM statistical_series_source_mapping
                WHERE source_id=? AND provider_binding_key=?
                """, Integer.class, source, providerBindingKey()));
    }

    @Test
    void inactiveIdentityIsNotSilentlyReactivated() {
        UUID country = country();
        var registration = series(country);
        UUID seriesId = service.registerSeries(registration);
        jdbc.update("UPDATE statistical_series SET active=false WHERE id=?", seriesId);

        assertThrows(IllegalStateException.class,
                () -> service.registerSeries(registration));
        assertEquals(false, jdbc.queryForObject(
                "SELECT active FROM statistical_series WHERE id=?",
                Boolean.class, seriesId));
    }

    @Test
    void inactiveProviderBindingIsNotSilentlyReactivated() {
        UUID country = country();
        UUID source = source();
        UUID seriesId = service.registerSeries(series(country));
        var registration = mapping(seriesId, source, "십억원");
        UUID mappingId = service.registerSourceMapping(registration);
        jdbc.update("UPDATE statistical_series_source_mapping SET active=false WHERE id=?", mappingId);

        assertThrows(IllegalStateException.class,
                () -> service.registerSourceMapping(registration));
        assertEquals(false, jdbc.queryForObject(
                "SELECT active FROM statistical_series_source_mapping WHERE id=?",
                Boolean.class, mappingId));
    }

    @Test
    void schemaRejectsInvalidEnumsForeignKeysAndWhitespaceMetadata() {
        UUID country = country();
        UUID source = source();
        UUID seriesId = service.registerSeries(series(country));

        rejectsSql("""
                INSERT INTO statistical_series(
                    subject_entity_id, metric, frequency, adjustment, value_kind)
                VALUES (?, 'BAD', 'QUARTERLY', 'SEASONALLY_ADJUSTED', 'LEVEL')
                """, country);
        rejectsSql("""
                INSERT INTO statistical_series(
                    subject_entity_id, metric, frequency, adjustment, value_kind)
                VALUES (?, 'REAL_GDP', 'QUARTERLY', 'SEASONALLY_ADJUSTED', 'LEVEL')
                """, UUID.randomUUID());
        rejectsSql("""
                INSERT INTO statistical_series_source_mapping(
                    statistical_series_id, source_id, provider_binding_key,
                    provider_series_name, provider_item_name, provider_frequency_code,
                    provider_unit_name, metadata_locator)
                VALUES (?, ?, ' key ', 'Series', 'Item', 'Q', '십억원', 'locator')
                """, seriesId, source);
    }

    private void rejectsSql(String sql, Object... args) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try {
                assertThrows(org.springframework.dao.DataAccessException.class,
                        () -> jdbc.update(sql, args));
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void additiveUpgradeFromV11PreservesCoreRowsAndCreatesEmptySeriesTables() throws Exception {
        String schema = "series_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var base = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("11").load();
        try {
            base.migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.execute("""
                        INSERT INTO entity(id,entity_type,canonical_name,canonical_key,country_code)
                        VALUES ('00000000-0000-0000-0000-000000000001','COUNTRY',
                                'Existing Country','existing-country','KR')
                        """);
                sql.execute("""
                        INSERT INTO source(id,source_type,name,external_key)
                        VALUES ('00000000-0000-0000-0000-000000000002','GOVERNMENT',
                                'Existing Source','existing-source')
                        """);

                var upgrade = Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).target("12").load();
                assertEquals(1, upgrade.migrate().migrationsExecuted);
                upgrade.validate();

                try (var rows = sql.executeQuery("""
                        SELECT (SELECT count(*) FROM entity),
                               (SELECT count(*) FROM source),
                               (SELECT count(*) FROM statistical_series),
                               (SELECT count(*) FROM statistical_series_source_mapping)
                        """)) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                    assertEquals(1, rows.getInt(2));
                    assertEquals(0, rows.getInt(3));
                    assertEquals(0, rows.getInt(4));
                }
                connection.setSchema("public");
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private UUID country() {
        return countries.registerOrReuse().entityId();
    }

    private UUID source() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO source(
                    id, source_type, name, canonical_domain, external_key, active)
                VALUES (?, 'GOVERNMENT', 'ECOS source fixture', 'ecos.bok.or.kr', ?, true)
                """, id, "fixture-source-" + id);
        return id;
    }

    private static StatisticalSeriesRegistration series(UUID countryId) {
        return new StatisticalSeriesRegistration(
                countryId,
                StatisticalMetric.REAL_GDP,
                StatisticalFrequency.QUARTERLY,
                StatisticalAdjustment.SEASONALLY_ADJUSTED,
                StatisticalValueKind.LEVEL);
    }

    private static StatisticalSeriesSourceMappingRegistration mapping(
            UUID seriesId, UUID sourceId, String unitName) {
        return new StatisticalSeriesSourceMappingRegistration(
                seriesId,
                sourceId,
                providerBindingKey(),
                "2.1.2.1.2. 경제활동별 GDP 및 GNI(계절조정, 실질, 분기)",
                "국내총생산(시장가격, GDP)",
                "Q",
                unitName,
                "StatisticItemList/json/kr/200Y104/1/1000");
    }

    private static String providerBindingKey() {
        return "StatisticSearch:200Y104:1400:-:-:-:Q";
    }
}
