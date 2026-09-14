package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;
import static com.aira.api.market.opendart.OpenDartProviderException.Category.*;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.ingestion.SourceAwareEarningsIngestionService;
import com.aira.api.market.query.CompanyFinancialFactsQuery;
import com.aira.api.market.query.CompanyFinancialFactsQueryInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FilingEvidenceSharingPostgresE2ETests {
    @Autowired OpenDartAnnualCfsAdapter adapter;
    @Autowired OpenDartFilingPersistence persistence;
    @Autowired OfficialCompanyDataPreparationOperation preparation;
    @Autowired SourceAwareEarningsIngestionService legacyIngestion;
    @Autowired CompanyFinancialFactsQuery query;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Fixtures fixtures;
    private static final List<FactPredicate> METRICS = List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME);

    @Test
    void sharesOneFilingEvidenceAcrossFactsAndReprocessing() {
        Fixture f = fixture();
        var first = save(f);
        var before = jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", first.companyId());
        var repeated = save(f);
        assertEquals(first.receipts(), repeated.receipts());
        assertEquals(before, jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", first.companyId()));
        var a = first.receipts().getFirst();
        var b = first.receipts().getLast();
        assertEquals(a.evidenceId(), b.evidenceId());
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", f.receipt));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", witnessIdentity(f)));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion WHERE evidence_id=?", a.evidenceId()));
        assertEquals(2, count("SELECT count(*) FROM fact WHERE subject_entity_id=?", first.companyId()));
        assertEquals(2, count("SELECT count(*) FROM fact_period_evidence p JOIN evidence e ON e.id=p.evidence_id WHERE e.external_id=?", witnessIdentity(f)));
        assertEquals(0, count("SELECT count(*) FROM fact_assertion a JOIN evidence e ON e.id=a.evidence_id WHERE e.external_id=?", witnessIdentity(f)));
        assertEquals(1, count("SELECT count(*) FROM source WHERE source_type='REGULATOR' AND external_key='opendart'"));
        byte[] valueHash = jdbc.queryForObject("SELECT content_hash FROM evidence WHERE id=?", byte[].class, a.evidenceId());
        byte[] otherHash = jdbc.queryForObject("SELECT e.content_hash FROM evidence e JOIN fact_assertion a ON a.evidence_id=e.id WHERE a.fact_id=?", byte[].class, b.factId());
        assertArrayEquals(valueHash, otherHash);
        assertEquals(f.receipt, jdbc.queryForObject("SELECT external_id FROM evidence WHERE id=?", String.class, a.evidenceId()));
        assertEquals(1, count("SELECT revision FROM evidence WHERE external_id=?", witnessIdentity(f)));
        var result = query.find(new CompanyFinancialFactsQueryInput(first.companyId(), Set.of(), LocalDate.of(2025,4,1), LocalDate.of(2025,9,30)));
        assertEquals(2, result.facts().size());
        assertTrue(result.facts().stream().allMatch(view -> view.evidenceId().equals(a.evidenceId())));
        assertEquals(0, count("SELECT count(*) FROM event WHERE id=? AND occurred_at IS NOT NULL", a.eventId()));
        assertEquals(0, count("SELECT count(*) FROM event_evidence ee JOIN evidence e ON e.id=ee.evidence_id WHERE e.external_id=?", witnessIdentity(f)));
    }

    @Test
    void conflictingHashIsRejectedAndSecondMetricFailureRollsBackFirst() {
        Fixture f = fixture();
        var saved = save(f);
        f.values = OpenDartWitnessFixtures.values(f.corp, f.receipt, "1001", "100");
        assertThrows(RuntimeException.class, () -> save(f));
        assertEquals(new BigDecimal("1000"), jdbc.queryForObject("SELECT value_number FROM fact WHERE id=?", BigDecimal.class, saved.receipts().getFirst().factId()));
        Fixture invalid = fixture();
        invalid.values = OpenDartWitnessFixtures.values(invalid.corp, invalid.receipt, "1000", "malformed");
        assertThrows(OpenDartProviderException.class, () -> save(invalid));
        assertNoWrites(invalid);
    }

    @Test
    void missingWitnessWithCurrentAccMtNeverCreatesCompanyOrIdentifier() {
        Fixture blocked = fixture();
        blocked.witness = new OpenDartPeriodWitnessResponse("013", null);
        Fixture other = fixture();
        var failure = assertThrows(OpenDartProviderException.class,
                () -> preparation.prepareByStockCodes(List.of(blocked.stock, other.stock), 2025));
        assertEquals(PERIOD_WITNESS_MISSING, failure.category());
        assertNoWrites(blocked);
        assertNoWrites(other);
    }

    @Test
    void currentDecemberMetadataDoesNotCorrectShortWitnessAndLaterBlockedFilingDoesNotRollbackSuccess() {
        Fixture good = fixture();
        Fixture blocked = fixture();
        blocked.witness = OpenDartWitnessFixtures.witness(blocked.corp, blocked.receipt, "2025.09.30 현재");
        assertThrows(OpenDartProviderException.class,
                () -> preparation.prepareByStockCodes(List.of(good.stock, blocked.stock), 2025));
        assertEquals(2, count("SELECT count(*) FROM fact f JOIN entity_external_identifier i ON i.entity_id=f.subject_entity_id WHERE i.namespace='OPENDART' AND i.identifier_value=?", good.corp));
        assertEquals(LocalDate.of(2025,4,1), jdbc.queryForObject("SELECT f.period_start FROM fact f JOIN fact_assertion a ON a.fact_id=f.id JOIN evidence e ON e.id=a.evidence_id WHERE e.external_id=? LIMIT 1", LocalDate.class, good.receipt));
        assertNoWrites(blocked);
    }

    @Test
    void verifiedLegacySamePeriodAddsPeriodLinksWithoutValueMutation() {
        Fixture f = fixture();
        var filing = adapter.prepare(f.context(), f.receipt, METRICS);
        UUID company = insertCompany(f);
        for (var input : filing.inputs(company, f.name)) legacyIngestion.ingest(input);
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", witnessIdentity(f)));
        var before = jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", company);
        save(f);
        save(f);
        assertEquals(before, jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", company));
        assertEquals(2, count("SELECT count(*) FROM fact_period_evidence p JOIN fact f ON f.id=p.fact_id WHERE f.subject_entity_id=?", company));
    }

    @Test
    void legacyMismatchIsBlockedInPreflightAndLockedRecheckWithoutCorrectedDuplicate() {
        Fixture f = fixture();
        var preparedBeforeLegacyWrite = adapter.prepare(f.context(), f.receipt, METRICS);
        UUID company = insertCompany(f);
        // Simulates a pre-existing legacy row whose period was formerly inferred.
        for (var input : preparedBeforeLegacyWrite.inputs(company, f.name)) {
            legacyIngestion.ingest(new com.aira.api.market.ingestion.SourceAwareEarningsIngestionInput(
                    input.source(), input.evidence(), input.subjectEntityId(), LocalDate.of(2025,12,31),
                    input.neutralTitle(), null, input.predicate(), input.numberValue(), input.currencyCode(),
                    LocalDate.of(2025,1,1), LocalDate.of(2025,12,31), input.assertionLocator()));
        }
        var before = jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", company);
        assertEquals(LEGACY_PERIOD_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> adapter.prepare(f.context(), f.receipt, METRICS)).category());
        assertEquals(LEGACY_PERIOD_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> persistence.persist(preparedBeforeLegacyWrite, f.record())).category());
        assertEquals(before, jdbc.queryForList("SELECT id,value_number,period_start,period_end,updated_at FROM fact WHERE subject_entity_id=? ORDER BY id", company));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", witnessIdentity(f)));
        assertEquals(0, count("SELECT count(*) FROM fact_period_evidence p JOIN fact f ON f.id=p.fact_id WHERE f.subject_entity_id=?", company));
    }

    @Test
    void changedSameRevisionWitnessRepresentationIsRejectedWithoutChangingFacts() {
        Fixture f = fixture();
        save(f);
        var originalHash = jdbc.queryForObject("SELECT content_hash FROM evidence WHERE external_id=?", byte[].class, witnessIdentity(f));
        f.witness = OpenDartWitnessFixtures.witness(f.corp, f.receipt, "2025.04.01~2025.09.30", "2025.09.29 현재");
        assertThrows(RuntimeException.class, () -> save(f));
        assertArrayEquals(originalHash, jdbc.queryForObject("SELECT content_hash FROM evidence WHERE external_id=?", byte[].class, witnessIdentity(f)));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion a JOIN evidence e ON e.id=a.evidence_id WHERE e.external_id=?", f.receipt));
    }

    @Test
    void periodRelationshipFailureRollsBackCompanyIdentifierAndBothRepresentations() {
        Fixture f = fixture();
        String constraint = "test_period_failure_" + f.corp;
        // Test-only constraint in the isolated database injects a failure after all preceding writes.
        jdbc.execute("ALTER TABLE fact_period_evidence ADD CONSTRAINT " + constraint
                + " CHECK (locator NOT LIKE '%/" + f.receipt + "/%')");
        try {
            assertThrows(RuntimeException.class, () -> save(f));
            assertNoWrites(f);
        } finally {
            jdbc.execute("ALTER TABLE fact_period_evidence DROP CONSTRAINT " + constraint);
        }
    }

    @Test
    void concurrentDifferentPeriodsForOneFilingCannotSilentlyCreateTwoFacts() throws Exception {
        Fixture f = fixture();
        var first = adapter.prepare(f.context(), f.receipt, METRICS);
        f.witness = OpenDartWitnessFixtures.witness(f.corp, f.receipt, "2025.01.01~2025.12.31");
        var second = adapter.prepare(f.context(), f.receipt, METRICS);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(executor.submit(() -> racePersist(first, f, start)),
                    executor.submit(() -> racePersist(second, f, start)));
            start.countDown();
            var outcomes = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(outcomes, "PASS"));
            assertEquals(1, Collections.frequency(outcomes, "LEGACY_PERIOD_MISMATCH"));
        }
        assertEquals(2, count("SELECT count(*) FROM fact_assertion a JOIN evidence e ON e.id=a.evidence_id WHERE e.external_id=?", f.receipt));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", witnessIdentity(f)));
    }

    @Test
    void differentFilingsKeepExistingValueConflictSemantics() {
        Fixture f = fixture();
        var first = save(f);
        String oldReceipt = f.receipt;
        f.receipt = receipt();
        f.receipts.add(f.receipt);
        f.values = OpenDartWitnessFixtures.values(f.corp, f.receipt, "2000", "100");
        f.witness = OpenDartWitnessFixtures.witness(f.corp, f.receipt, "2025.04.01~2025.09.30");
        save(f);
        assertEquals("CONFLICTING", jdbc.queryForObject("SELECT status FROM fact WHERE id=?", String.class, first.receipts().getFirst().factId()));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?", first.receipts().getFirst().factId()));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", oldReceipt));
    }

    @Test
    void unmappedAdapterIngestionCannotInferCompanyFromNames() {
        Fixture f = fixture();
        assertEquals(PERIOD_WITNESS_IDENTITY_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> adapter.ingestFiling(f.context(), f.receipt, METRICS)).category());
        assertNoWrites(f);
    }

    @Test
    void concurrentIdenticalFilingReusesOneCompanyAndBothRepresentations() throws Exception {
        Fixture f = fixture();
        var prepared = adapter.prepare(f.context(), f.receipt, METRICS);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> racePersist(prepared, f, start));
            var second = executor.submit(() -> racePersist(prepared, f, start));
            start.countDown();
            assertEquals("PASS", first.get(20, TimeUnit.SECONDS));
            assertEquals("PASS", second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, count("SELECT count(*) FROM entity WHERE canonical_name=?", f.name));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion a JOIN evidence e ON e.id=a.evidence_id WHERE e.external_id=?", f.receipt));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", witnessIdentity(f)));
    }

    private String racePersist(OpenDartPreparedFiling filing, Fixture f, CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { persistence.persist(filing, f.record()); return "PASS"; }
        catch (OpenDartProviderException blocked) { return blocked.category().name(); }
    }

    private OpenDartFilingPersistence.Saved save(Fixture f) {
        return persistence.persist(adapter.prepare(f.context(), f.receipt, METRICS), f.record());
    }

    private Fixture fixture() {
        Fixture f = new Fixture();
        fixtures.rows.put(f.corp, f);
        return f;
    }

    private UUID insertCompany(Fixture f) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO entity(id,entity_type,canonical_name,canonical_key) VALUES (?,'COMPANY',?,?)", id, f.name, f.name);
        jdbc.update("INSERT INTO entity_external_identifier(entity_id,namespace,identifier_type,identifier_value) VALUES (?,'OPENDART','CORP_CODE',?)", id, f.corp);
        return id;
    }

    private void assertNoWrites(Fixture f) {
        assertEquals(0, count("SELECT count(*) FROM entity WHERE canonical_name=?", f.name));
        assertEquals(0, count("SELECT count(*) FROM entity_external_identifier WHERE namespace='OPENDART' AND identifier_value=?", f.corp));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id IN (?,?)", f.receipt, witnessIdentity(f)));
        assertEquals(0, count("SELECT count(*) FROM fact f JOIN entity e ON e.id=f.subject_entity_id WHERE e.canonical_name=?", f.name));
    }

    private int count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }
    private static String witnessIdentity(Fixture f) { return "fnlttSinglAcnt:CFS:11011:" + f.receipt; }
    private static String receipt() { return String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L)); }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Fixture f : fixtures.rows.values()) {
                var companies = jdbc.queryForList("SELECT id FROM entity WHERE canonical_name=?", UUID.class, f.name);
                for (UUID company : companies) {
                    var events = jdbc.queryForList("SELECT event_id FROM event_entity WHERE entity_id=?", UUID.class, company);
                    jdbc.update("DELETE FROM fact_period_evidence WHERE fact_id IN (SELECT id FROM fact WHERE subject_entity_id=?)", company);
                    jdbc.update("DELETE FROM fact_assertion WHERE fact_id IN (SELECT id FROM fact WHERE subject_entity_id=?)", company);
                    jdbc.update("DELETE FROM fact WHERE subject_entity_id=?", company);
                    for (UUID event : events) {
                        jdbc.update("DELETE FROM event_evidence WHERE event_id=?", event);
                        jdbc.update("DELETE FROM event_entity WHERE event_id=?", event);
                        jdbc.update("DELETE FROM event WHERE id=?", event);
                    }
                    jdbc.update("DELETE FROM entity_external_identifier WHERE entity_id=?", company);
                    jdbc.update("DELETE FROM entity WHERE id=?", company);
                }
                for (String receipt : f.receipts) jdbc.update("DELETE FROM evidence WHERE external_id IN (?,?)", receipt, "fnlttSinglAcnt:CFS:11011:" + receipt);
            }
        });
        fixtures.rows.clear();
    }

    static class Fixture {
        final String corp = String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        final String name = "Witness fixture " + UUID.randomUUID();
        final String stock = corp.substring(0,6);
        String receipt = receipt();
        final Set<String> receipts = new HashSet<>(Set.of(receipt));
        OpenDartFinancialResponse values = OpenDartWitnessFixtures.values(corp, receipt, "1000", "100");
        OpenDartPeriodWitnessResponse witness = OpenDartWitnessFixtures.witness(corp, receipt, "2025.04.01~2025.09.30", "2025.09.30 현재");
        OpenDartAnnualCfsContext context() { return new OpenDartAnnualCfsContext(corp, 2025, "11011", "CFS"); }
        OpenDartCompanyDirectoryRecord record() { return new OpenDartCompanyDirectoryRecord(corp, name, null, stock, LocalDate.of(2026,1,1)); }
    }

    static class Fixtures { final Map<String,Fixture> rows = new ConcurrentHashMap<>(); }

    @TestConfiguration
    static class Configuration {
        @Bean Fixtures fixtures() { return new Fixtures(); }
        @Bean @Primary OpenDartAnnualCfsClient valueClient(Fixtures fixtures) {
            return (corp, year) -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return fixtures.rows.get(corp).values; };
        }
        @Bean @Primary OpenDartPeriodWitnessClient periodClient(Fixtures fixtures) {
            return (corp, year) -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return fixtures.rows.get(corp).witness; };
        }
        @Bean @Primary OpenDartCompanyDirectoryClient directoryClient(Fixtures fixtures) {
            return () -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new OpenDartCompanyDirectory(fixtures.rows.values().stream().map(Fixture::record).toList()); };
        }
        @Bean @Primary OpenDartCompanyProfileClient profileClient(Fixtures fixtures) {
            return corp -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new OpenDartCompanyProfile(corp, fixtures.rows.get(corp).name, 12); };
        }
    }
}
