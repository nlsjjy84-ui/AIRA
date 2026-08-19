package com.aira.api.market.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.service.SourceRegistration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class SourceAwareIngestionPostgresE2ETests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-19T00:00:00Z");

    @Autowired private SourceAwareEarningsIngestionService ingestion;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void persistsCompleteProvenanceAndReusesOnlyTheSameSource() {
        String suffix = UUID.randomUUID().toString();
        String firstSourceKey = "ingestion-e2e-source-" + suffix;
        String secondSourceKey = "ingestion-e2e-other-" + suffix;
        UUID subjectId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            insertCompany(subjectId, "COMPANY:INGESTION-E2E:" + suffix);
            SourceAwareIngestionResult first = ingestion.ingest(
                    input(firstSourceKey, "evidence-1-" + suffix, subjectId));
            SourceAwareIngestionResult repeated = ingestion.ingest(
                    input(firstSourceKey, "evidence-2-" + suffix, subjectId));
            SourceAwareIngestionResult different = ingestion.ingest(
                    input(secondSourceKey, "evidence-3-" + suffix, subjectId));
            entityManager.flush();

            assertEquals(first.source().getId(), repeated.source().getId());
            assertNotEquals(first.evidence().getId(), repeated.evidence().getId());
            assertNotEquals(first.source().getId(), different.source().getId());
            assertEquals(1, count("SELECT count(*) FROM source WHERE external_key = ?",
                    firstSourceKey));
            assertEquals(1, count("SELECT count(*) FROM source WHERE external_key = ?",
                    secondSourceKey));
            assertEquals(3, count("""
                    SELECT count(*)
                    FROM fact_assertion fa
                    JOIN evidence e ON e.id = fa.evidence_id
                    JOIN source s ON s.id = e.source_id
                    WHERE fa.fact_id = ? AND s.external_key IN (?, ?)
                    """, first.fact().getId(), firstSourceKey, secondSourceKey));
            assertEquals(first.source().getId(), jdbc.queryForObject("""
                    SELECT s.id
                    FROM fact_assertion fa
                    JOIN evidence e ON e.id = fa.evidence_id
                    JOIN source s ON s.id = e.source_id
                    WHERE fa.fact_id = ? AND e.id = ?
                    """, UUID.class, first.fact().getId(), first.evidence().getId()));
            status.setRollbackOnly();
        });

        assertEquals(0, count("SELECT count(*) FROM source WHERE external_key IN (?, ?)",
                firstSourceKey, secondSourceKey));
    }

    @Test
    void downstreamFailureRollsBackSourceAndEvidence() {
        String suffix = UUID.randomUUID().toString();
        String sourceKey = "ingestion-rollback-source-" + suffix;
        String evidenceId = "ingestion-rollback-evidence-" + suffix;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(
                status -> ingestion.ingest(input(sourceKey, evidenceId, UUID.randomUUID()))));

        assertEquals(0, count("SELECT count(*) FROM source WHERE external_key = ?", sourceKey));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id = ?", evidenceId));
    }

    private SourceAwareEarningsIngestionInput input(
            String sourceKey, String evidenceId, UUID subjectId) {
        return new SourceAwareEarningsIngestionInput(
                new SourceRegistration(SourceType.REGULATOR, sourceKey,
                        "Ingestion E2E Regulator", "example.gov"),
                new EvidenceRegistration(EvidenceType.DISCLOSURE, evidenceId,
                        "https://example.gov/" + evidenceId, "2026 Q2 filing",
                        new byte[] {1, 2, 3}, "table:revenue", NOW, NOW, 1),
                subjectId, LocalDate.of(2026, 6, 30), "2026 Q2 results", NOW,
                FactPredicate.REVENUE, new BigDecimal("100.00"), "USD",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "table:revenue");
    }

    private void insertCompany(UUID id, String canonicalKey) {
        jdbc.update("""
                INSERT INTO entity (
                    id, entity_type, canonical_name, canonical_key, active,
                    created_at, updated_at
                ) VALUES (?, 'COMPANY', 'Ingestion E2E Company', ?, true,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, canonicalKey);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
