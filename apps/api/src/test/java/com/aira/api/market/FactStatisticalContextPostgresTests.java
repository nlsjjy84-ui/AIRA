package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FactStatisticalContextPostgresTests {
    @Autowired DataSource dataSource;

    @Test
    void v14EnforcesRealGdpContextAndRetainsMatchingRelationship() throws Exception {
        withV14Schema(connection -> {            seedStatisticalFixture(connection);
            UUID orphan = UUID.fromString("00000000-0000-0000-0000-000000000101");
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> {
                insertRealGdp(connection, orphan, "00000000-0000-0000-0000-000000000001");
                insertAssertion(connection, orphan);
            }));
            assertEquals(0, count(connection, "SELECT count(*) FROM fact WHERE id='" + orphan + "'"));

            UUID valid = UUID.fromString("00000000-0000-0000-0000-000000000102");
            inTransaction(connection, () -> {
                insertRealGdp(connection, valid, "00000000-0000-0000-0000-000000000001");
                insertAssertion(connection, valid);
                exec(connection, """
                        INSERT INTO fact_statistical_context(fact_id,statistical_series_id,canonical_unit)
                        VALUES ('00000000-0000-0000-0000-000000000102',
                                '00000000-0000-0000-0000-000000000010','KRW_BILLION')
                        """);
            });
            assertEquals(1, count(connection,
                    "SELECT count(*) FROM fact_statistical_context WHERE fact_id='" + valid + "'"));

            assertThrows(SQLException.class, () -> inTransaction(connection, () -> exec(connection,
                    "DELETE FROM fact_statistical_context WHERE fact_id='" + valid + "'")));
            assertEquals(1, count(connection,
                    "SELECT count(*) FROM fact_statistical_context WHERE fact_id='" + valid + "'"));

            UUID moved = UUID.fromString("00000000-0000-0000-0000-000000000104");
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> {
                insertRealGdp(connection, moved, "00000000-0000-0000-0000-000000000001");
                insertAssertion(connection, moved);
                exec(connection, """
                        UPDATE fact_statistical_context
                        SET fact_id='00000000-0000-0000-0000-000000000104'
                        WHERE fact_id='00000000-0000-0000-0000-000000000102'
                        """);
            }));
            assertEquals(1, count(connection,
                    "SELECT count(*) FROM fact_statistical_context WHERE fact_id='" + valid + "'"));
            assertEquals(0, count(connection, "SELECT count(*) FROM fact WHERE id='" + moved + "'"));

            assertThrows(SQLException.class, () -> inTransaction(connection, () -> exec(connection, """
                    UPDATE statistical_series
                    SET subject_entity_id='00000000-0000-0000-0000-000000000002'
                    WHERE id='00000000-0000-0000-0000-000000000010'
                    """)));
            assertEquals("00000000-0000-0000-0000-000000000001",
                    scalar(connection, "SELECT subject_entity_id::text FROM statistical_series WHERE id='00000000-0000-0000-0000-000000000010'"));

            UUID revenue = UUID.fromString("00000000-0000-0000-0000-000000000103");
            inTransaction(connection, () -> {
                insertRevenue(connection, revenue);
                insertAssertion(connection, revenue);
            });
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> exec(connection, """
                    INSERT INTO fact_statistical_context(fact_id,statistical_series_id,canonical_unit)
                    VALUES ('00000000-0000-0000-0000-000000000103',
                            '00000000-0000-0000-0000-000000000010','KRW_BILLION')
                    """)));
            assertEquals(0, count(connection,
                    "SELECT count(*) FROM fact_statistical_context WHERE fact_id='" + revenue + "'"));
        });
    }

    @Test
    void v14RejectsInvalidRealGdpShapeUnitAndMismatchedSeries() throws Exception {
        withV14Schema(connection -> {
            seedStatisticalFixture(connection);
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> exec(connection, """
                    INSERT INTO fact(
                        id,subject_entity_id,predicate,status,value_type,value_number,currency_code,
                        period_start,period_end,dedup_key)
                    VALUES ('00000000-0000-0000-0000-000000000201',
                            '00000000-0000-0000-0000-000000000001','REAL_GDP','SUPPORTED',
                            'NUMBER',596692.8,'KRW',DATE '2026-04-01',DATE '2026-06-30',
                            decode(repeat('21',32),'hex'))
                    """)));
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> exec(connection, """
                    INSERT INTO fact(
                        id,subject_entity_id,predicate,status,value_type,value_number,
                        period_start,period_end,dedup_key)
                    VALUES ('00000000-0000-0000-0000-000000000202',
                            '00000000-0000-0000-0000-000000000001','REAL_GDP','SUPPORTED',
                            'NUMBER',596692.8,DATE '2026-04-02',DATE '2026-06-30',
                            decode(repeat('22',32),'hex'))
                    """)));

            UUID mismatched = UUID.fromString("00000000-0000-0000-0000-000000000203");
            assertThrows(SQLException.class, () -> inTransaction(connection, () -> {
                insertRealGdp(connection, mismatched, "00000000-0000-0000-0000-000000000002");
                insertAssertion(connection, mismatched);
                exec(connection, """
                        INSERT INTO fact_statistical_context(fact_id,statistical_series_id,canonical_unit)
                        VALUES ('00000000-0000-0000-0000-000000000203',
                                '00000000-0000-0000-0000-000000000010','KRW_BILLION')
                        """);
            }));
            assertEquals(0, count(connection,
                    "SELECT count(*) FROM fact WHERE id='" + mismatched + "'"));

            UUID valid = UUID.fromString("00000000-0000-0000-0000-000000000204");
            inTransaction(connection, () -> {
                insertRealGdp(connection, valid, "00000000-0000-0000-0000-000000000001");
                insertAssertion(connection, valid);
                exec(connection, """
                        INSERT INTO fact_statistical_context(fact_id,statistical_series_id,canonical_unit)
                        VALUES ('00000000-0000-0000-0000-000000000204',
                                '00000000-0000-0000-0000-000000000010','KRW_BILLION')
                        """);
            });
            assertThrows(SQLException.class, () -> exec(connection, """
                    INSERT INTO fact_statistical_context(fact_id,statistical_series_id,canonical_unit)
                    VALUES ('00000000-0000-0000-0000-000000000204',
                            '00000000-0000-0000-0000-000000000010','BAD')
                    """));
            assertEquals("KRW_BILLION", scalar(connection,
                    "SELECT canonical_unit FROM fact_statistical_context WHERE fact_id='" + valid + "'"));
        });
    }

    @Test
    void additiveUpgradeFromV13PreservesExistingRowsAndCreatesEmptyContext() throws Exception {        String schema = "fact_context_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var base = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("13").load();
        try {
            base.migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.execute("""
                        INSERT INTO entity(id,entity_type,canonical_name,canonical_key,country_code)
                        VALUES ('00000000-0000-0000-0000-000000000301','COMPANY',
                                'Existing Company','COMPANY:existing','KR')
                        """);
                sql.execute("""
                        INSERT INTO source(id,source_type,name)
                        VALUES ('00000000-0000-0000-0000-000000000302','REGULATOR','Existing Source')
                        """);
                sql.execute("""
                        INSERT INTO evidence(id,source_id,evidence_type,original_url,content_hash,collected_at,status)
                        VALUES ('00000000-0000-0000-0000-000000000303',
                                '00000000-0000-0000-0000-000000000302','OFFICIAL_DATA',
                                'https://example.org/existing',decode('01','hex'),CURRENT_TIMESTAMP,'ACTIVE')
                        """);
                connection.setAutoCommit(false);
                sql.execute("""
                        INSERT INTO fact(id,subject_entity_id,predicate,status,value_type,value_number,
                                         currency_code,period_start,period_end,dedup_key)
                        VALUES ('00000000-0000-0000-0000-000000000304',
                                '00000000-0000-0000-0000-000000000301','REVENUE','SUPPORTED',
                                'NUMBER',100,'KRW',DATE '2025-01-01',DATE '2025-12-31',
                                decode(repeat('31',32),'hex'))
                        """);
                sql.execute("""
                        INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number)
                        VALUES ('00000000-0000-0000-0000-000000000304',
                                '00000000-0000-0000-0000-000000000303','value','NUMBER',100)
                        """);
                connection.commit();
                connection.setAutoCommit(true);

                var upgrade = Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).target("15").load();
                assertEquals(2, upgrade.migrate().migrationsExecuted);
                upgrade.validate();
                assertEquals(1, count(connection, "SELECT count(*) FROM fact"));
                assertEquals(1, count(connection, "SELECT count(*) FROM fact_assertion"));
                assertEquals(0, count(connection, "SELECT count(*) FROM fact_statistical_context"));
                assertEquals("REVENUE", scalar(connection,
                        "SELECT predicate FROM fact WHERE id='00000000-0000-0000-0000-000000000304'"));
                assertEquals(3, count(connection, """
                        SELECT count(*)
                        FROM pg_trigger t
                        JOIN pg_class c ON c.oid = t.tgrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = current_schema()
                          AND tgname IN ('real_gdp_fact_requires_context',
                                         'statistical_context_matches_fact',
                                         'statistical_series_retains_fact_context')
                          AND tgdeferrable AND tginitdeferred
                        """));
            }
        } finally {            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void withV14Schema(SqlWork work) throws Exception {
        String schema = "fact_context_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("15").load();
        try {
            flyway.migrate();
            flyway.validate();
            try (var connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                work.run(connection);
                connection.setSchema("public");
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void seedStatisticalFixture(Connection connection) throws SQLException {
        exec(connection, """
                INSERT INTO entity(id,entity_type,canonical_name,canonical_key,country_code)
                VALUES
                    ('00000000-0000-0000-0000-000000000001','COUNTRY','Republic of Korea','COUNTRY:KR','KR'),
                    ('00000000-0000-0000-0000-000000000002','COUNTRY','United States','COUNTRY:US','US'),
                    ('00000000-0000-0000-0000-000000000003','COMPANY','Fixture Company','COMPANY:fixture','KR')
                """);        exec(connection, """
                INSERT INTO source(id,source_type,name)
                VALUES ('00000000-0000-0000-0000-000000000004','GOVERNMENT','ECOS fixture')
                """);
        exec(connection, """
                INSERT INTO evidence(id,source_id,evidence_type,external_id,original_url,
                                     content_hash,collected_at,revision,status)
                VALUES ('00000000-0000-0000-0000-000000000005',
                        '00000000-0000-0000-0000-000000000004','OFFICIAL_DATA','fixture',
                        'https://example.org/ecos',decode('01','hex'),CURRENT_TIMESTAMP,1,'ACTIVE')
                """);
        exec(connection, """
                INSERT INTO statistical_series(
                    id,subject_entity_id,metric,frequency,adjustment,value_kind,active)
                VALUES ('00000000-0000-0000-0000-000000000010',
                        '00000000-0000-0000-0000-000000000001','REAL_GDP','QUARTERLY',
                        'SEASONALLY_ADJUSTED','LEVEL',true)
                """);
    }

    private void insertRealGdp(Connection connection, UUID id, String countryId) throws SQLException {
        exec(connection, """
                INSERT INTO fact(
                    id,subject_entity_id,predicate,status,value_type,value_number,
                    period_start,period_end,dedup_key)
                VALUES ('%s','%s','REAL_GDP','SUPPORTED','NUMBER',596692.8,
                        DATE '2026-04-01',DATE '2026-06-30',decode(md5('%s')||md5('%s'),'hex'))
                """.formatted(id, countryId, id, id));
    }
    private void insertRevenue(Connection connection, UUID id) throws SQLException {
        exec(connection, """
                INSERT INTO fact(
                    id,subject_entity_id,predicate,status,value_type,value_number,currency_code,
                    period_start,period_end,dedup_key)
                VALUES ('%s','00000000-0000-0000-0000-000000000003','REVENUE','SUPPORTED',
                        'NUMBER',100,'KRW',DATE '2025-01-01',DATE '2025-12-31',
                        decode(md5('%s')||md5('%s'),'hex'))
                """.formatted(id, id, id));
    }

    private void insertAssertion(Connection connection, UUID factId) throws SQLException {
        exec(connection, """
                INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number)
                SELECT id,'00000000-0000-0000-0000-000000000005','value',value_type,value_number FROM fact WHERE id='%s'
                """.formatted(factId));
    }

    private void inTransaction(Connection connection, SqlAction action) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            action.run();
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
    private void exec(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
    private String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
