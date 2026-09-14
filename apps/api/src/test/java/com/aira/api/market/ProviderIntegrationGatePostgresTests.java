package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.krx.*;
import com.aira.api.market.opendart.*;
import com.aira.api.market.query.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class ProviderIntegrationGatePostgresTests {
    @Autowired OpenDartFilingPersistence dart;
    @Autowired KrxPersistence krx;
    @Autowired JdbcTemplate jdbc;
    @Autowired FinancialHistoricalExactQuery historicalExact;
    @Autowired FinancialCurrentQuery financialCurrent;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void sameDisplayNameAndTickerNeverMergeCompanySecurityOrProvenance() throws Exception {
        String corp = String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        String receipt = String.format("%014d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000_000L));
        var context = new OpenDartAnnualCfsContext(corp, 2025, "11011", "CFS");
        var period = OpenDartPeriodWitnessResolver.resolve(context, receipt,
                new OpenDartPeriodWitnessResponse("000", List.of(
                        new OpenDartPeriodWitnessResponse.Row(corp, "2025", "11011", receipt,
                                "CFS", "2025.04.01 ~ 2025.09.30"))), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(receipt.getBytes(StandardCharsets.UTF_8));
        var value = new EvidenceRegistration(EvidenceType.DISCLOSURE, receipt,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt,
                "OpenDART annual CFS filing", hash, null, null, OffsetDateTime.parse("2026-01-01T00:00:00Z"), 1);
        var filing = new OpenDartPreparedFiling(context, value, period,
                List.of(new OpenDartPreparedFiling.Metric(FactPredicate.REVENUE,
                        new BigDecimal("100"), "KRW", "CFS/IS/ifrs_Revenue/thstrm_amount")));
        var record = new OpenDartCompanyDirectoryRecord(corp, "Gate Same Name", null, "000009", LocalDate.of(2026, 1, 1));
        var company = dart.persist(filing, record);
        assertEquals(company, dart.persist(filing, record));
        var exact = historicalExact.find(company.companyId(), period.start(), period.end(),
                receipt, java.util.Set.of("REVENUE"));
        assertEquals(1, exact.facts().size());
        assertEquals(receipt, exact.facts().getFirst().evidenceExternalId());
        assertEquals(exact, financialCurrent.find(new FinancialCurrentQuery.ExplicitSelection(
                company.companyId(), period.start(), period.end(), receipt, java.util.Set.of("REVENUE"))));
        assertThrows(CompanyFinancialFactsQueryException.class, () -> historicalExact.find(
                company.companyId(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31),
                receipt, java.util.Set.of("REVENUE")));
        assertThrows(CompanyFinancialFactsQueryException.class, () -> historicalExact.find(
                company.companyId(), period.start(), period.end(), "99999999999999", java.util.Set.of("REVENUE")));
        assertThrows(IllegalArgumentException.class, () -> financialCurrent.find(null));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM fact_period_evidence WHERE fact_id=?", company.receipts().getFirst().factId());
            assertThrows(CompanyFinancialFactsQueryException.class, () -> historicalExact.find(
                    company.companyId(), period.start(), period.end(), receipt, java.util.Set.of("REVENUE")));
            status.setRollbackOnly();
        });

        // Equal display names and ticker text deliberately exercise the forbidden cross-provider identity shortcut.
        LocalDate marketDate = LocalDate.of(2040, 1, 8);
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, marketDate, List.of(
                Map.of("ISU_CD", "KR7GATE0000001", "ISU_SRT_CD", "000009", "ISU_NM", "Gate Same Name")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, marketDate, List.of(
                Map.of("BAS_DD", "20400108", "ISU_CD", "000009", "TDD_CLSPRC", "100")));
        var packet = KrxPreparedPacket.stock(base, daily);
        krx.stock(packet);
        krx.stock(packet);

        UUID securityId = jdbc.queryForObject("""
                SELECT entity_id FROM entity_external_identifier
                WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value='KR7GATE0000001'
                """, UUID.class);
        assertNotEquals(company.companyId(), securityId);
        assertEquals("COMPANY", jdbc.queryForObject("SELECT entity_type FROM entity WHERE id=?", String.class, company.companyId()));
        assertEquals("SECURITY", jdbc.queryForObject("SELECT entity_type FROM entity WHERE id=?", String.class, securityId));
        assertEquals(0, count("SELECT count(*) FROM entity_external_identifier WHERE entity_id=? AND namespace='KRX'", company.companyId()));
        assertEquals(0, count("SELECT count(*) FROM entity_external_identifier WHERE entity_id=? AND namespace='OPENDART'", securityId));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", receipt));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", "KRX_OPENAPI:stk_bydd_trd:20400108"));
        assertEquals(0, count("""
                SELECT count(*) FROM fact f JOIN fact_assertion a ON a.fact_id=f.id
                JOIN evidence e ON e.id=a.evidence_id JOIN source s ON s.id=e.source_id
                WHERE (f.subject_entity_id=? AND s.external_key='krx')
                   OR (f.subject_entity_id=? AND s.external_key='opendart')
                """, company.companyId(), securityId));
        assertEquals(LocalDate.of(2025, 4, 1), jdbc.queryForObject("SELECT period_start FROM fact WHERE subject_entity_id=?", LocalDate.class, company.companyId()));
        assertEquals(marketDate, jdbc.queryForObject("SELECT period_start FROM fact WHERE subject_entity_id=?", LocalDate.class, securityId));
        assertEquals(marketDate, jdbc.queryForObject("SELECT period_end FROM fact WHERE subject_entity_id=?", LocalDate.class, securityId));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE original_url ILIKE '%AUTH_KEY%' OR original_url ILIKE '%synthetic-secret%'"));
        assertArrayEquals(hash, jdbc.queryForObject("SELECT content_hash FROM evidence WHERE external_id=?", byte[].class, receipt));
        assertArrayEquals(daily.hash(), jdbc.queryForObject("SELECT content_hash FROM evidence WHERE external_id=?", byte[].class,
                "KRX_OPENAPI:stk_bydd_trd:20400108"));
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
