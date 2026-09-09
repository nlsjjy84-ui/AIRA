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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Autowired private EarningsIngestionBoundary ingestion;
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
            IngestionReceipt first = ingestion.ingest(
                    input(firstSourceKey, "evidence-1-" + suffix, subjectId));
            IngestionReceipt repeated = ingestion.ingest(
                    input(firstSourceKey, "evidence-2-" + suffix, subjectId));
            IngestionReceipt different = ingestion.ingest(
                    input(secondSourceKey, "evidence-3-" + suffix, subjectId));
            entityManager.flush();

            assertEquals(first.sourceId(), repeated.sourceId());
            assertNotEquals(first.evidenceId(), repeated.evidenceId());
            assertNotEquals(first.sourceId(), different.sourceId());
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
                    """, first.factId(), firstSourceKey, secondSourceKey));
            assertEquals(first.sourceId(), jdbc.queryForObject("""
                    SELECT s.id
                    FROM fact_assertion fa
                    JOIN evidence e ON e.id = fa.evidence_id
                    JOIN source s ON s.id = e.source_id
                    WHERE fa.fact_id = ? AND e.id = ?
                    """, UUID.class, first.factId(), first.evidenceId()));
            status.setRollbackOnly();
        });

        assertEquals(0, count("SELECT count(*) FROM source WHERE external_key IN (?, ?)",
                firstSourceKey, secondSourceKey));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameRevisionRecollectionPreservesOriginalProvenanceAndNewRevisionGetsNewId(boolean nullable) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            UUID subject = UUID.randomUUID();
            insertCompany(subject, "COMPANY:REVISION:" + suffix);
            var input = input("revision-" + suffix, suffix, subject);
            if (nullable) {
                var base = input.evidence();
                input = withEvidence(input, new EvidenceRegistration(base.evidenceType(), base.externalId(),
                        base.originalUrl(), null, base.contentHash(), null, null, NOW, 1));
            }
            var e = input.evidence();
            var first = ingestion.ingest(input);
            var recollected = new EvidenceRegistration(e.evidenceType(), e.externalId(),
                    e.originalUrl(), e.title(), e.contentHash(), e.locator(),
                    e.publishedAt() == null ? null : e.publishedAt().withOffsetSameInstant(java.time.ZoneOffset.ofHours(9)),
                    NOW.plusDays(1), 1);
            assertEquals(first.evidenceId(), ingestion.ingest(withEvidence(input, recollected)).evidenceId());
            assertEquals(NOW.toInstant(), jdbc.queryForObject("SELECT collected_at FROM evidence WHERE id=?",
                    OffsetDateTime.class, first.evidenceId()).toInstant());
            var revision = new EvidenceRegistration(e.evidenceType(), e.externalId(),
                    e.originalUrl(), e.title(), e.contentHash(), e.locator(), e.publishedAt(), NOW.plusDays(1), 2);
            var second = ingestion.ingest(withEvidence(input, revision));
            assertNotEquals(first.evidenceId(), second.evidenceId());
            assertEquals(second.evidenceId(), ingestion.ingest(withEvidence(input, revision)).evidenceId());
            status.setRollbackOnly();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "url", "title", "hash", "locator", "publishedAt",
            "nullTitle", "nullLocator", "nullPublishedAt"})
    void sameRevisionProvenanceConflictIsRejectedBeforeDownstreamWrites(String field) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            UUID subject = UUID.randomUUID();
            insertCompany(subject, "COMPANY:CONFLICT:" + suffix);
            var input = input("conflict-" + suffix, suffix, subject);
            var first = ingestion.ingest(input);
            entityManager.flush();
            String before = jdbc.queryForObject("SELECT row_to_json(e)::text FROM evidence e WHERE id=?",
                    String.class, first.evidenceId());
            var e = input.evidence();
            var conflict = new EvidenceRegistration(
                    field.equals("type") ? EvidenceType.OTHER : e.evidenceType(), e.externalId(),
                    field.equals("url") ? "https://different.test" : e.originalUrl(),
                    field.equals("nullTitle") ? null : field.equals("title") ? "different" : e.title(),
                    field.equals("hash") ? new byte[] {9} : e.contentHash(),
                    field.equals("nullLocator") ? null : field.equals("locator") ? "different" : e.locator(),
                    field.equals("nullPublishedAt") ? null : field.equals("publishedAt") ? NOW.plusDays(1) : e.publishedAt(),
                    NOW.plusDays(1), 1);
            assertThrows(IllegalStateException.class, () -> ingestion.ingest(withEvidence(input, conflict)));
            assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", suffix));
            assertEquals(1, count("SELECT count(*) FROM fact_assertion WHERE evidence_id=?", first.evidenceId()));
            assertEquals(before, jdbc.queryForObject("SELECT row_to_json(e)::text FROM evidence e WHERE id=?",
                    String.class, first.evidenceId()));
            status.setRollbackOnly();
        });
    }

    private SourceAwareEarningsIngestionInput withEvidence(
            SourceAwareEarningsIngestionInput input, EvidenceRegistration evidence) {
        return new SourceAwareEarningsIngestionInput(input.source(), evidence, input.subjectEntityId(),
                input.reportingPeriodEnd(), input.neutralTitle(), input.occurredAt(), input.predicate(),
                input.numberValue(), input.currencyCode(), input.periodStart(), input.periodEnd(), input.assertionLocator());
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
