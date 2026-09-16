package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.market.service.KoreaCountryBootstrapOperation;
import javax.sql.DataSource;
import java.util.UUID;
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
class KoreaCountryBootstrapPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired KoreaCountryBootstrapOperation operation;
    @Autowired DataSource dataSource;

    @Test
    void repeatedBootstrapReusesOneCanonicalKoreaRow() {
        var first = operation.registerOrReuse();
        var second = operation.registerOrReuse();

        assertEquals(first.entityId(), second.entityId());
        assertEquals("COUNTRY:KR", first.canonicalKey());
        assertEquals("Republic of Korea", first.canonicalName());
        assertEquals("KR", first.countryCode());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM entity
                WHERE entity_type='COUNTRY'
                  AND canonical_key='COUNTRY:KR'
                  AND canonical_name='Republic of Korea'
                  AND country_code='KR'
                  AND market_code IS NULL
                  AND symbol IS NULL
                  AND active=true
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM entity_external_identifier
                WHERE entity_id=?
                """, Integer.class, first.entityId()));
    }

    @Test
    void driftedExistingKoreaBlocksWithoutOverwrite() {
        jdbc.update("""
                INSERT INTO entity(
                    entity_type, canonical_name, canonical_key,
                    country_code, active)
                VALUES ('COUNTRY','Korea','COUNTRY:KR','KR',true)
                """);

        assertThrows(IllegalStateException.class, operation::registerOrReuse);
        assertEquals("Korea", jdbc.queryForObject("""
                SELECT canonical_name FROM entity
                WHERE canonical_key='COUNTRY:KR'
                """, String.class));
    }

    @Test
    void schemaRejectsInvalidCountryIdentityShapes() {
        rejectsSql("""
                INSERT INTO entity(
                    entity_type, canonical_name, canonical_key, country_code)
                VALUES ('COUNTRY','Missing code','COUNTRY:ZZ',NULL)
                """);
        rejectsSql("""
                INSERT INTO entity(
                    entity_type, canonical_name, canonical_key, country_code)
                VALUES ('COUNTRY','Wrong key','COUNTRY:YY','XX')
                """);
        rejectsSql("""
                INSERT INTO entity(
                    entity_type, canonical_name, canonical_key,
                    market_code, country_code)
                VALUES ('COUNTRY','Market leak','COUNTRY:QX','KRX','QX')
                """);
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
    void additiveUpgradeFromV12PreservesValidExistingRows() throws Exception {
        String schema = "country_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var base = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("12").load();
        try {
            base.migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                try {
                    sql.execute("""
                        INSERT INTO entity(
                            id,entity_type,canonical_name,canonical_key,country_code)
                        VALUES ('00000000-0000-0000-0000-000000000001','COUNTRY',
                                'Republic of Korea','COUNTRY:KR','KR')
                        """);
                sql.execute("""
                        INSERT INTO entity(
                            id,entity_type,canonical_name,canonical_key,country_code)
                        VALUES ('00000000-0000-0000-0000-000000000002','COMPANY',
                                'Existing Company','COMPANY:existing','KR')
                        """);

                var upgrade = Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).load();
                assertTrue(upgrade.migrate().migrationsExecuted > 0);
                upgrade.validate();
                try (var rows = sql.executeQuery("""
                        SELECT
                            (SELECT count(*) FROM entity),
                            (SELECT count(*) FROM entity WHERE entity_type='COUNTRY'),
                            (SELECT count(*) FROM entity WHERE entity_type='COMPANY')
                        """)) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                    assertEquals(1, rows.getInt(2));
                    assertEquals(1, rows.getInt(3));
                }

                try (var index = sql.executeQuery("""
                        SELECT count(*)
                        FROM pg_indexes
                        WHERE schemaname = current_schema()
                          AND indexname = 'uq_entity_country_code_country'
                        """)) {
                    assertTrue(index.next());
                    assertEquals(1, index.getInt(1));
                }
                } finally {
                    connection.setSchema("public");
                }
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void additiveUpgradeBlocksInvalidLegacyCountryWithoutRepairingIt() throws Exception {
        String schema = "country_block_" + UUID.randomUUID().toString().replace("-", "");
        var base = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("12").load();
        try {
            base.migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.execute("""
                        INSERT INTO entity(
                            id,entity_type,canonical_name,canonical_key,country_code)
                        VALUES ('00000000-0000-0000-0000-000000000003','COUNTRY',
                                'Legacy Korea','legacy-country-kr','KR')
                        """);
                connection.setSchema("public");
            }

            var upgrade = Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).load();
            assertThrows(org.flywaydb.core.api.FlywayException.class, upgrade::migrate);

            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                try (var rows = sql.executeQuery("""
                        SELECT canonical_key FROM entity
                        WHERE id='00000000-0000-0000-0000-000000000003'
                        """)) {
                    assertTrue(rows.next());
                    assertEquals("legacy-country-kr", rows.getString(1));
                }
                connection.setSchema("public");
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
