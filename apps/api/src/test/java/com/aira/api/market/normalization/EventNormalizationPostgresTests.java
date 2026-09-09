package com.aira.api.market.normalization;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.domain.*;
import com.aira.api.market.ingestion.*;
import com.aira.api.market.service.SourceRegistration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class EventNormalizationPostgresTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T00:00:00Z");
    private static final LocalDate PERIOD = LocalDate.of(2025, 3, 31);
    @Autowired JdbcTemplate jdbc;
    @Autowired EarningsIngestionBoundary ingestion;
    @Autowired EarningsEventNormalizationService normalization;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;

    @Test
    void differentSourcesConcurrentlyIngestOneCanonicalEventAndOneSubjectLink() throws Exception {
        Fixture f = fixture();
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var one = pool.submit(() -> { ready.countDown(); await(start); return ingestion.ingest(input(f, 0)); });
            var two = pool.submit(() -> { ready.countDown(); await(start); return ingestion.ingest(input(f, 1)); });
            await(ready);
            start.countDown();
            var a = one.get(20, TimeUnit.SECONDS);
            var b = two.get(20, TimeUnit.SECONDS);
            assertEquals(a.eventId(), b.eventId());
            assertNotEquals(a.evidenceId(), b.evidenceId());
            assertEquals(1, count("SELECT count(*) FROM event WHERE dedup_key=?", key(f)));
            assertEquals(1, count("SELECT count(*) FROM event_entity WHERE event_id=?", a.eventId()));
            assertEquals(2, count("SELECT count(*) FROM event_evidence WHERE event_id=?", a.eventId()));
            assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM event WHERE id=?", String.class, a.eventId()));
            assertNull(jdbc.queryForObject("SELECT occurred_at FROM event WHERE id=?", OffsetDateTime.class, a.eventId()));
        } finally { cleanup(f); }
    }

    @Test
    void concurrentSameEvidenceAndEntityLinksAreIdempotent() throws Exception {
        Fixture f = fixture();
        try (var pool = Executors.newFixedThreadPool(2)) {
            UUID evidence = insertEvidence(f, NOW);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<UUID> task = () -> { ready.countDown(); await(start); return normalize(f, evidence).getId(); };
            var one = pool.submit(task);
            var two = pool.submit(task);
            await(ready);
            start.countDown();
            UUID id = one.get(20, TimeUnit.SECONDS);
            assertEquals(id, two.get(20, TimeUnit.SECONDS));
            assertEquals(1, count("SELECT count(*) FROM event WHERE dedup_key=?", key(f)));
            assertEquals(1, count("SELECT count(*) FROM event_entity WHERE event_id=?", id));
            assertEquals(1, count("SELECT count(*) FROM event_evidence WHERE event_id=?", id));
        } finally { cleanup(f); }
    }

    @Test
    void olderTransactionWithStaleManagedEventCannotOverwriteNewerCommittedObservation() throws Exception {
        Fixture f = fixture();
        CountDownLatch releaseOlder = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            UUID first = insertEvidence(f, NOW);
            UUID older = insertEvidence(f, NOW.plusDays(1));
            UUID newer = insertEvidence(f, NOW.plusDays(2));
            UUID id = normalize(f, first).getId();
            CountDownLatch loaded = new CountDownLatch(1);
            var oldTransaction = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                em.find(Event.class, id); // Deliberately hold the pre-update JPA snapshot.
                loaded.countDown();
                await(releaseOlder);
                return normalize(f, older).getId();
            }));
            try {
                await(loaded);
                assertEquals(id, normalize(f, newer).getId());
            } finally { releaseOlder.countDown(); }
            assertEquals(id, oldTransaction.get(20, TimeUnit.SECONDS));
            assertEquals(NOW.plusDays(2).toInstant(), jdbc.queryForObject(
                    "SELECT last_observed_at FROM event WHERE id=?", OffsetDateTime.class, id).toInstant());
            assertEquals(3, count("SELECT count(*) FROM event_evidence WHERE event_id=?", id));
        } finally { releaseOlder.countDown(); cleanup(f); }
    }

    @Test
    void relationFailureRollsBackCandidateAndTerminalEventsCannotBeReobserved() {
        Fixture f = fixture();
        try {
            UUID evidence = insertEvidence(f, NOW);
            assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.execute("ALTER TABLE event_evidence ADD CONSTRAINT ck_event_test_reject CHECK (relation_type <> 'SUPPORTS') NOT VALID");
                normalize(f, evidence);
            }));
            assertEquals(0, count("SELECT count(*) FROM event WHERE dedup_key=?", key(f)));
            assertEquals(0, count("SELECT count(*) FROM event_entity WHERE entity_id=?", f.company()));
            UUID id = normalize(f, evidence).getId();
            for (String state : new String[] {"MERGED", "DISCARDED"}) {
                jdbc.update("UPDATE event SET status=? WHERE id=?", state, id);
                assertThrows(IllegalStateException.class, () -> normalize(f, evidence));
                assertEquals(state, jdbc.queryForObject("SELECT status FROM event WHERE id=?", String.class, id));
            }
        } finally { cleanup(f); }
    }

    @Test
    void publicationAndReportingPeriodDoNotSupplyUnknownOccurrence() {
        Fixture f = fixture();
        try {
            var receipt = ingestion.ingest(input(f, 0));
            assertNull(jdbc.queryForObject("SELECT occurred_at FROM event WHERE id=?", OffsetDateTime.class, receipt.eventId()));
            assertEquals(PERIOD, jdbc.queryForObject("SELECT period_end FROM fact WHERE id=?", LocalDate.class, receipt.factId()));
            assertEquals(NOW.minusDays(1).toInstant(), jdbc.queryForObject("SELECT published_at FROM evidence WHERE id=?",
                    OffsetDateTime.class, receipt.evidenceId()).toInstant());
            assertEquals(NOW.toInstant(), jdbc.queryForObject("SELECT collected_at FROM evidence WHERE id=?",
                    OffsetDateTime.class, receipt.evidenceId()).toInstant());
        } finally { cleanup(f); }
    }

    private Event normalize(Fixture f, UUID evidence) {
        return normalization.normalize(new EarningsNormalizationInput(f.company(), evidence, PERIOD, "2025 fiscal year", null));
    }

    private SourceAwareEarningsIngestionInput input(Fixture f, int source) {
        return new SourceAwareEarningsIngestionInput(new SourceRegistration(SourceType.REGULATOR,
                f.prefix() + source, "Event concurrency", "example.test"),
                new EvidenceRegistration(EvidenceType.DISCLOSURE, f.prefix() + source,
                        "https://example.test/filing", "2025 fiscal year", new byte[] {1}, null,
                        NOW.minusDays(1), NOW, 1), f.company(), PERIOD, "2025 fiscal year", null,
                FactPredicate.REVENUE, BigDecimal.TEN, "KRW", PERIOD.minusYears(1).plusDays(1), PERIOD, "revenue");
    }

    private Fixture fixture() {
        UUID company = UUID.randomUUID();
        String prefix = "event-concurrency-" + company + "-";
        jdbc.update("INSERT INTO entity(id,entity_type,canonical_name,canonical_key) VALUES(?,'COMPANY','Event test',?)", company, prefix);
        return new Fixture(company, prefix);
    }

    private UUID insertEvidence(Fixture f, OffsetDateTime collectedAt) {
        UUID source = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        jdbc.update("INSERT INTO source(id,source_type,name,external_key) VALUES(?,'REGULATOR','Event test',?)", source, f.prefix() + source);
        jdbc.update("INSERT INTO evidence(id,source_id,evidence_type,original_url,content_hash,collected_at,revision,status) VALUES(?,?,'DISCLOSURE','https://example.test',?,?,1,'ACTIVE')",
                evidence, source, new byte[] {1}, collectedAt);
        return evidence;
    }

    private byte[] key(Fixture f) { return EarningsEventDedupKey.create(f.prefix(), PERIOD); }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for test coordination"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private void cleanup(Fixture f) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM fact_assertion WHERE fact_id IN (SELECT id FROM fact WHERE subject_entity_id=?)", f.company());
            jdbc.update("DELETE FROM fact WHERE subject_entity_id=?", f.company());
            jdbc.update("DELETE FROM event_evidence WHERE event_id IN (SELECT id FROM event WHERE dedup_key=?)", key(f));
            jdbc.update("DELETE FROM event_entity WHERE event_id IN (SELECT id FROM event WHERE dedup_key=?)", key(f));
            jdbc.update("DELETE FROM event WHERE dedup_key=?", key(f));
            jdbc.update("DELETE FROM evidence WHERE source_id IN (SELECT id FROM source WHERE external_key LIKE ?)", f.prefix() + "%");
            jdbc.update("DELETE FROM source WHERE external_key LIKE ?", f.prefix() + "%");
            jdbc.update("DELETE FROM entity WHERE id=?", f.company());
        });
    }
    private record Fixture(UUID company, String prefix) {}
}
