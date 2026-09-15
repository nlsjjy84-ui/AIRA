package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.domain.*;
import com.aira.api.market.repository.*;
import com.aira.api.market.query.*;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
class FactPeriodEvidencePostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired FactPeriodEvidenceRegistrationStore registrations;
    @Autowired FactPeriodEvidenceRepository periods;
    @Autowired FactAssertionRepository assertions;
    @Autowired CompanyFinancialFactsQuery query;
    @Autowired DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private final LocalDate start = LocalDate.of(2025, 4, 1);
    private final LocalDate end = LocalDate.of(2025, 9, 30);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    void linksManyToManyWithoutChangingValueProvenanceOrExactPublicRead() {
        UUID company = company();
        UUID source = source();
        UUID value = evidence(source);
        UUID witness = evidence(source);
        UUID secondWitness = evidence(source);
        UUID first = fact(company, value);
        UUID second = fact(company, value);
        assertTrue(periods.findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(first).isEmpty());
        register(first, witness, "period / 1", now);
        register(first, witness, "period / 1", now.plusDays(1));
        register(first, secondWitness, "period / 2", now);
        register(second, witness, "period / 1", now);
        em.clear();
        var links = periods.findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(first);
        assertEquals(2, links.size());
        assertTrue(links.stream().allMatch(link -> link.getCreatedAt().equals(now)));
        assertEquals(1, periods.findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(second).size());
        assertEquals(1, assertions.findWithProvenanceByFactIds(Set.of(first)).size());
        assertEquals(value, assertions.findWithProvenanceByFactIds(Set.of(first)).getFirst().getEvidence().getId());
        var result = query.find(new CompanyFinancialFactsQueryInput(company, Set.of(), start, end));
        assertEquals(2, result.facts().size());
        assertTrue(result.facts().stream().allMatch(view -> view.evidenceId().equals(value)));
        assertEquals(start, em.find(Fact.class, first).getPeriodStart());
        assertEquals(end, em.find(Fact.class, first).getPeriodEnd());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM fact_assertion WHERE evidence_id IN (?,?)", Integer.class, witness, secondWitness));
    }

    @Test
    void refusesConflictingLocatorWithoutOverwriting() {
        UUID source = source();
        UUID value = evidence(source);
        UUID fact = fact(company(), value);
        UUID witness = evidence(source);
        register(fact, witness, "original", now);
        var failure = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> register(fact, witness, "changed", now));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("original", jdbc.queryForObject("SELECT locator FROM fact_period_evidence WHERE fact_id=?", String.class, fact));
    }

    @Test
    void rejectsMismatchBeforeInsertingLink() {
        UUID source = source();
        UUID value = evidence(source);
        UUID fact = fact(company(), value);
        assertThrows(IllegalArgumentException.class, () -> FactPeriodEvidence.verified(
                em.find(Fact.class, fact), em.find(Evidence.class, value), "p", start.minusDays(1), end, now));
        assertTrue(periods.findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(fact).isEmpty());
    }

    @Test
    void schemaEnforcesForeignKeysUniquenessLocatorAndRestrictsDeletion() {
        UUID source = source();
        UUID value = evidence(source);
        UUID fact = fact(company(), value);
        UUID witness = evidence(source);
        register(fact, witness, "p", now);
        rejectsSql("INSERT INTO fact_period_evidence(fact_id,evidence_id,locator) VALUES (?,?,'p')", fact, witness);
        rejectsSql("INSERT INTO fact_period_evidence(fact_id,evidence_id,locator) VALUES (?,?,'p')", UUID.randomUUID(), witness);
        rejectsSql("INSERT INTO fact_period_evidence(fact_id,evidence_id,locator) VALUES (?,?,'p')", fact, UUID.randomUUID());
        for (String locator : new String[] {null, "", " ", "\t\n"}) {
            rejectsSql("INSERT INTO fact_period_evidence(fact_id,evidence_id,locator) VALUES (?,?,?)", fact, value, locator);
        }
        rejectsSql("DELETE FROM evidence WHERE id=?", witness);
        // Remove the value link first so only the period FK prevents deleting the Fact.
        jdbc.update("DELETE FROM fact_assertion WHERE fact_id=?", fact);
        rejectsSql("DELETE FROM fact WHERE id=?", fact);
        rejectsSql("UPDATE evidence SET id=? WHERE id=?", UUID.randomUUID(), witness);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM fact_period_evidence WHERE fact_id=?", Integer.class, fact));
    }

    private void rejectsSql(String sql, Object... args) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try {
                assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(sql, args));
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void additiveUpgradePreservesExistingRowsWithoutInventingPeriodEvidence() throws Exception {
        String schema = "period_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var base = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("10").load();
        try {
            base.migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                connection.setSchema(schema);
                try {
                    sql.execute("INSERT INTO entity(id,entity_type,canonical_name,canonical_key) VALUES ('00000000-0000-0000-0000-000000000001','COMPANY','Existing','existing')");
                sql.execute("INSERT INTO source(id,source_type,name) VALUES ('00000000-0000-0000-0000-000000000002','REGULATOR','Existing')");
                sql.execute("INSERT INTO evidence(id,source_id,evidence_type,original_url,content_hash,collected_at,status) VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','OFFICIAL_DATA','https://example.org',decode('01','hex'),CURRENT_TIMESTAMP,'ACTIVE')");
                connection.setAutoCommit(false);
                sql.execute("INSERT INTO fact(id,subject_entity_id,predicate,status,value_type,value_number,currency_code,period_start,period_end,dedup_key) VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000001','REVENUE','SUPPORTED','NUMBER',100,'KRW','2025-04-01','2025-09-30',decode(repeat('01',32),'hex'))");
                sql.execute("INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number) VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003','value','NUMBER',100)");
                connection.commit();
                connection.setAutoCommit(true);
                var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
                assertEquals(7, upgrade.migrate().migrationsExecuted);
                upgrade.validate();
                try (var rows = sql.executeQuery("SELECT (SELECT count(*) FROM fact_period_evidence), (SELECT count(*) FROM fact_assertion), (SELECT count(*) FROM evidence), value_number, period_start, period_end FROM fact")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                    assertEquals(1, rows.getInt(2));
                    assertEquals(1, rows.getInt(3));
                    assertEquals(100, rows.getInt(4));
                    assertEquals(start, rows.getObject(5, LocalDate.class));
                    assertEquals(end, rows.getObject(6, LocalDate.class));
                }
                } finally {
                    connection.setSchema("public");
                }
            }
        } finally {
            // Only this randomly named test schema is removed; never the application schema.
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void register(UUID fact, UUID evidence, String locator, OffsetDateTime time) {
        registrations.registerOrReuse(FactPeriodEvidence.verified(em.find(Fact.class, fact),
                em.find(Evidence.class, evidence), locator, start, end, time));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void relationshipRollsBackWithItsFactAndEvidence() {
        UUID[] ids = new org.springframework.transaction.support.TransactionTemplate(transactions)
                .execute(status -> {
                    UUID source = source();
                    UUID witness = evidence(source);
                    UUID fact = fact(company(), evidence(source));
                    register(fact, witness, "p", now);
                    assertEquals(1, periods.findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(fact).size());
                    status.setRollbackOnly();
                    return new UUID[] {fact, witness};
                });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM fact_period_evidence WHERE fact_id=?", Integer.class, ids[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM fact WHERE id=?", Integer.class, ids[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM evidence WHERE id=?", Integer.class, ids[1]));
    }

    private UUID company() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO entity(id,entity_type,canonical_name,canonical_key) VALUES (?,'COMPANY','Period fixture',?)", id, id.toString());
        return id;
    }

    private UUID source() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO source(id,source_type,name) VALUES (?,'REGULATOR','Period fixture')", id);
        return id;
    }

    private UUID evidence(UUID source) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evidence(id,source_id,evidence_type,external_id,original_url,content_hash,collected_at,status)
                VALUES (?,?,'OFFICIAL_DATA',?,'https://example.org/period',decode('01','hex'),CURRENT_TIMESTAMP,'ACTIVE')
                """, id, source, id.toString());
        return id;
    }

    private UUID fact(UUID company, UUID evidence) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fact(id,subject_entity_id,predicate,status,value_type,value_number,currency_code,period_start,period_end,dedup_key)
                VALUES (?,?,'REVENUE','SUPPORTED','NUMBER',100,'KRW',?,?,decode(md5(?)||md5(?),'hex'))
                """, id, company, start, end, id.toString(), id.toString());
        jdbc.update("INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number) VALUES (?,?,'value','NUMBER',100)", id, evidence);
        return id;
    }
}
