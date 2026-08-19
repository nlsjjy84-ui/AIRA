package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aira.api.market.domain.FactPredicate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FilingEvidenceSharingPostgresE2ETests {
    @Autowired private OpenDartAnnualCfsAdapter adapter;
    @Autowired private FixtureClient client;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void sharesOneFilingEvidenceAcrossFactsAndReprocessing() {
        String suffix = UUID.randomUUID().toString();
        String corpCode = uniqueCorpCode();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            UUID companyId = insertCompanyAndMapping(suffix, corpCode);
            client.respond(success("1000", "100"));

            var context = new OpenDartAnnualCfsContext(corpCode, 2025, "11011", "CFS", 12);
            var first = adapter.ingestFiling(context,
                    List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));
            var repeated = adapter.ingestFiling(context,
                    List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));

            assertEquals(first.get(0).evidenceId(), first.get(1).evidenceId());
            assertEquals(first.get(0).evidenceId(), repeated.get(0).evidenceId());
            assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id = ?",
                    receipt(suffix)));
            assertEquals(2, count("""
                    SELECT count(*) FROM fact_assertion WHERE evidence_id = ?
                    """, first.getFirst().evidenceId()));
            assertEquals(2, count("SELECT count(*) FROM fact WHERE subject_entity_id = ?",
                    companyId));
            assertArrayEquals(jdbc.queryForObject(
                            "SELECT content_hash FROM evidence WHERE id = ?", byte[].class,
                            first.getFirst().evidenceId()),
                    jdbc.queryForObject("""
                            SELECT e.content_hash FROM evidence e
                            JOIN fact_assertion fa ON fa.evidence_id = e.id
                            WHERE fa.fact_id = ?
                            """, byte[].class, first.getLast().factId()));
            assertEquals(receipt(suffix), jdbc.queryForObject(
                    "SELECT external_id FROM evidence WHERE id = ?", String.class,
                    first.getFirst().evidenceId()));
            status.setRollbackOnly();
        });
    }

    @Test
    void conflictingHashIsRejectedAndSecondMetricFailureRollsBackFirst() {
        String suffix = UUID.randomUUID().toString();
        String corpCode = uniqueCorpCode();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            insertCompanyAndMapping(suffix, corpCode);
            client.respond(success("1000", "100"));
            var context = new OpenDartAnnualCfsContext(corpCode, 2025, "11011", "CFS", 12);
            adapter.ingestFiling(context, List.of(FactPredicate.REVENUE));
            client.respond(success("1001", "100"));
            assertThrows(IllegalStateException.class, () -> adapter.ingestFiling(
                    context, List.of(FactPredicate.REVENUE)));
            status.setRollbackOnly();
        });

        String rollbackSuffix = UUID.randomUUID().toString();
        String rollbackCorpCode = uniqueCorpCode();
        assertThrows(UnexpectedRollbackException.class, () -> transaction.executeWithoutResult(
                status -> {
                    insertCompanyAndMapping(rollbackSuffix, rollbackCorpCode);
                    client.respond(success("1000", "malformed"));
                    var context = new OpenDartAnnualCfsContext(
                            rollbackCorpCode, 2025, "11011", "CFS", 12);
                    assertThrows(OpenDartProviderException.class, () -> adapter.ingestFiling(
                            context,
                            List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME)));
                }));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id = ?",
                receipt(rollbackSuffix)));
    }

    private UUID insertCompanyAndMapping(String suffix, String corpCode) {
        UUID companyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO entity (id, entity_type, canonical_name, canonical_key, active,
                    created_at, updated_at)
                VALUES (?, 'COMPANY', 'Filing Evidence E2E', ?, true,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId, "COMPANY:FILING-EVIDENCE-E2E:" + suffix);
        jdbc.update("""
                INSERT INTO entity_external_identifier (
                    entity_id, namespace, identifier_type, identifier_value, created_at)
                VALUES (?, 'OPENDART', 'CORP_CODE', ?, CURRENT_TIMESTAMP)
                """, companyId, corpCode);
        return companyId;
    }

    private OpenDartFinancialResponse success(String revenue, String operatingIncome) {
        return new OpenDartFinancialResponse("000", "OK", List.of(
                row("ifrs_Revenue", revenue), row("dart_OperatingIncomeLoss", operatingIncome)));
    }

    private OpenDartFinancialRow row(String accountId, String amount) {
        return new OpenDartFinancialRow(client.receiptNumber(), "2025", "11011", "CFS", "IS",
                accountId, "display label", "2025.01.01 ~ 2025.12.31", amount, "KRW");
    }

    private String receipt(String suffix) {
        return client.receiptNumber();
    }

    private String uniqueCorpCode() {
        return String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        FixtureClient fixtureClient() {
            return new FixtureClient();
        }
    }

    static final class FixtureClient implements OpenDartAnnualCfsClient {
        private final AtomicReference<OpenDartFinancialResponse> response = new AtomicReference<>();
        private final String receiptNumber = "e2e-" + UUID.randomUUID();

        void respond(OpenDartFinancialResponse value) {
            response.set(value);
        }

        String receiptNumber() {
            return receiptNumber;
        }

        @Override
        public OpenDartFinancialResponse fetch(String corpCode, int businessYear) {
            return response.get();
        }
    }
}
