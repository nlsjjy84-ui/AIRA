package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.ingestion.EarningsIngestionBoundary;
import com.aira.api.market.ingestion.IngestionReceipt;
import com.aira.api.market.ingestion.SourceAwareEarningsIngestionInput;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenDartAnnualCfsAdapterTests {
    private static final OpenDartAnnualCfsContext CONTEXT =
            new OpenDartAnnualCfsContext("00126380", 2025, "11011", "CFS");
    private static final String RECEIPT = "20260331000123";
    @Mock private OpenDartAnnualCfsClient client;
    @Mock private OpenDartPeriodWitnessClient witnesses;
    @Mock private OpenDartFilingPersistence persistence;
    @Mock private EarningsIngestionBoundary boundary;

    private OpenDartAnnualCfsAdapter adapter;
    private UUID entityId;
    private IngestionReceipt receipt;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new OpenDartAnnualCfsAdapter(client, witnesses, persistence,
                Clock.fixed(Instant.parse("2026-03-31T01:02:03Z"), ZoneOffset.UTC));
        entityId = UUID.randomUUID();
        receipt = new IngestionReceipt(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        lenient().when(witnesses.fetch("00126380", 2025)).thenReturn(
                OpenDartWitnessFixtures.witness("00126380", "20260331000123", "2025.01.01 ~ 2025.12.31"));
        lenient().when(persistence.persist(any(), org.mockito.ArgumentMatchers.isNull())).thenAnswer(call -> {
            OpenDartPreparedFiling filing = call.getArgument(0);
            return new OpenDartFilingPersistence.Saved(entityId,
                    filing.inputs(entityId, "Test Company").stream().map(boundary::ingest).toList());
        });
        lenient().when(boundary.ingest(any())).thenReturn(receipt);
    }

    @Test
    void separatesFiscalPeriodUnknownOccurrenceAndCollectionForMarchYearEnd() {
        var context = new OpenDartAnnualCfsContext("00126380", 2025, "11011", "CFS");
        var row = OpenDartWitnessFixtures.value("00126380", "20260601000123", "ifrs_Revenue", "10");
        when(witnesses.fetch("00126380", 2025)).thenReturn(OpenDartWitnessFixtures.witness(
                "00126380", "20260601000123", "2024.04.01 ~ 2025.03.31"));
        when(client.fetch("00126380", 2025)).thenReturn(success(row));
        adapter.ingest(new OpenDartAnnualCfsRequest(context, "20260601000123", FactPredicate.REVENUE));
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        var input = captor.getValue();
        assertEquals("2024-04-01", input.periodStart().toString());
        assertEquals("2025-03-31", input.reportingPeriodEnd().toString());
        org.junit.jupiter.api.Assertions.assertNull(input.occurredAt());
        org.junit.jupiter.api.Assertions.assertNull(input.evidence().publishedAt());
        assertEquals(Instant.parse("2026-03-31T01:02:03Z"), input.evidence().collectedAt().toInstant());
        assertTrue(input.neutralTitle().contains("2025"));
        assertFalse(input.neutralTitle().contains("2026"));
    }

    @Test
    void ingestsAnnualCfsRevenueThroughBoundary() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("1,234.50")));

        assertEquals(receipt, adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));

        verify(client).fetch("00126380", 2025);
        verify(persistence).preflight(any());
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        var input = captor.getValue();
        assertEquals(entityId, input.subjectEntityId());
        assertEquals(new BigDecimal("1234.50"), input.numberValue());
        assertEquals("KRW", input.currencyCode());
        assertEquals("2025-01-01", input.periodStart().toString());
        assertEquals("2025-12-31", input.periodEnd().toString());
        org.junit.jupiter.api.Assertions.assertNull(input.occurredAt());
        assertTrue(input.neutralTitle().contains("2025"));
        assertEquals(SourceType.REGULATOR, input.source().sourceType());
        assertEquals("opendart", input.source().externalKey());
        assertEquals("20260331000123", input.evidence().externalId());
        assertEquals("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260331000123",
                input.evidence().originalUrl());
        assertEquals("CFS/IS/ifrs_Revenue/thstrm_amount", input.assertionLocator());
    }

    @Test
    void mapsOnlyExactOperatingIncomeAccountId() {
        var exact = row("dart_OperatingIncomeLoss", "77");
        when(client.fetch("00126380", 2025)).thenReturn(success(exact));

        adapter.ingest(new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.OPERATING_INCOME));

        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        assertEquals(FactPredicate.OPERATING_INCOME, captor.getValue().predicate());
    }

    @Test
    void acceptsExactFullRevenueAliasAndRejectsAliasCollision() {
        when(client.fetch("00126380", 2025))
                .thenReturn(success(row("ifrs-full_Revenue", "100")));
        adapter.ingest(new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE));
        verify(boundary).ingest(any());

        when(client.fetch("00126380", 2025)).thenReturn(success(
                row("ifrs_Revenue", "100"), row("ifrs-full_Revenue", "100")));
        var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));
        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE, failure.category());
    }

    @Test
    void requiresExplicitExpectedReceiptIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
                new OpenDartAnnualCfsRequest(CONTEXT, null, FactPredicate.REVENUE));
        assertThrows(IllegalArgumentException.class, () ->
                new OpenDartAnnualCfsRequest(CONTEXT, "123", FactPredicate.REVENUE));
    }

    @Test
    void exactReceiptMissNeverFallsBackToAnotherFiling() {
        when(client.fetch("00126380", 2025)).thenReturn(success(
                OpenDartWitnessFixtures.value("00126380", "20260331000999", "ifrs_Revenue", "100")));

        var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));

        assertEquals(OpenDartProviderException.Category.NO_DATA, failure.category());
        verify(boundary, never()).ingest(any());
    }

    @Test
    void rejectsIdentityMismatchInsideExpectedReceipt() {
        when(client.fetch("00126380", 2025)).thenReturn(success(
                OpenDartWitnessFixtures.value("99999999", RECEIPT, "ifrs_Revenue", "100")));

        var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));

        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE, failure.category());
        verify(boundary, never()).ingest(any());
    }

    @Test
    void contextHasNoFiscalMonthInput() {
        assertTrue(java.util.Arrays.stream(OpenDartAnnualCfsContext.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("fiscalYearEndMonth")));
    }

    @Test
    void ingestsTwoMetricsFromOneFetchWithSharedFilingEvidence() {
        when(client.fetch("00126380", 2025)).thenReturn(success(
                revenueRow("1,000"), row("dart_OperatingIncomeLoss", "100")));

        var receipts = adapter.ingestFiling(CONTEXT, RECEIPT,
                List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));

        assertEquals(2, receipts.size());
        verify(client).fetch("00126380", 2025);
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary, org.mockito.Mockito.times(2)).ingest(captor.capture());
        var inputs = captor.getAllValues();
        assertArrayEquals(inputs.get(0).evidence().contentHash(),
                inputs.get(1).evidence().contentHash());
        assertEquals("20260331000123", inputs.get(0).evidence().externalId());
        assertEquals("20260331000123", inputs.get(1).evidence().externalId());
        assertEquals(null, inputs.get(0).evidence().locator());
    }

    @Test
    void filingHashIsStableAcrossProviderRowOrdering() {
        var revenue = revenueRow("1,000");
        var operatingIncome = row("dart_OperatingIncomeLoss", "100");
        when(client.fetch("00126380", 2025))
                .thenReturn(success(revenue, operatingIncome))
                .thenReturn(success(operatingIncome, revenue));

        adapter.ingestFiling(CONTEXT, RECEIPT, List.of(FactPredicate.REVENUE));
        adapter.ingestFiling(CONTEXT, RECEIPT, List.of(FactPredicate.REVENUE));

        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary, org.mockito.Mockito.times(2)).ingest(captor.capture());
        assertArrayEquals(captor.getAllValues().get(0).evidence().contentHash(),
                captor.getAllValues().get(1).evidence().contentHash());
    }

    @Test
    void multiMetricFilingDefinesOneTransactionBoundary() throws Exception {
        assertEquals(org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED,
                OpenDartAnnualCfsAdapter.class.getAnnotation(Transactional.class).propagation());
        assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                OpenDartFilingPersistence.class.getMethod("persist", OpenDartPreparedFiling.class,
                        OpenDartCompanyDirectoryRecord.class).getAnnotation(Transactional.class).propagation());
    }

    @Test
    void neverInfersUnsupportedAccountFromAccountName() {
        var unsupported = new OpenDartFinancialRow(RECEIPT, "2025", "11011", "00126380",
                null, "IS", "display", "custom_Revenue", "Revenue", null,
                null, "ignored term name", "100", null, null, null, null, "1", "KRW");
        when(client.fetch("00126380", 2025)).thenReturn(success(unsupported));

        var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));

        assertEquals(OpenDartProviderException.Category.NO_DATA, failure.category());
        verify(boundary, never()).ingest(any());
    }

    @Test
    void blockedPreflightNeverCallsPersistence() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("100")));
        org.mockito.Mockito.doThrow(new IllegalStateException("blocked preflight")).when(persistence).preflight(any());

        assertThrows(IllegalStateException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));
        verify(boundary, never()).ingest(any());
    }

    @Test
    void preservesLeadingZeroAndRejectsInvalidCorpCodes() {
        var request = new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE);
        assertEquals("00126380", request.context().corpCode());
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartAnnualCfsContext("126380", 2025, "11011", "CFS"));
    }

    @Test
    void parsesPositiveNegativeZeroDecimalAndCommaAmountsExactly() {
        assertEquals(new BigDecimal("1234.50"),
                OpenDartAnnualCfsAdapter.parseAmount("1,234.50"));
        assertEquals(new BigDecimal("-7"), OpenDartAnnualCfsAdapter.parseAmount("-7"));
        assertEquals(BigDecimal.ZERO, OpenDartAnnualCfsAdapter.parseAmount("0"));
    }

    @Test
    void rejectsBlankNullAndMalformedAmounts() {
        assertThrows(OpenDartProviderException.class,
                () -> OpenDartAnnualCfsAdapter.parseAmount(null));
        assertThrows(OpenDartProviderException.class,
                () -> OpenDartAnnualCfsAdapter.parseAmount("  "));
        assertThrows(OpenDartProviderException.class,
                () -> OpenDartAnnualCfsAdapter.parseAmount("not-money"));
    }

    @Test
    void classifiesProviderStatusesAndDoesNotTreatNoDataAsZero() {
        for (var entry : List.of(
                new StatusCase("013", OpenDartProviderException.Category.NO_DATA),
                new StatusCase("010", OpenDartProviderException.Category.AUTHENTICATION),
                new StatusCase("020", OpenDartProviderException.Category.RATE_LIMIT),
                new StatusCase("100", OpenDartProviderException.Category.INVALID_REQUEST),
                new StatusCase("800", OpenDartProviderException.Category.PROVIDER_FAILURE))) {
            when(client.fetch("00126380", 2025))
                    .thenReturn(new OpenDartFinancialResponse(entry.status(), "provider text", null));
            var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                    new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)));
            assertEquals(entry.category(), failure.category());
        }
        verify(boundary, never()).ingest(any());
    }

    @Test
    void deterministicFilingHashIgnoresCollectionTime() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("1,000")));
        adapter.ingest(new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE));
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        byte[] first = captor.getValue().evidence().contentHash();

        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("1,000")));
        adapter.ingest(new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE));
        verify(boundary, org.mockito.Mockito.times(2)).ingest(captor.capture());
        byte[] second = captor.getAllValues().getLast().evidence().contentHash();

        assertArrayEquals(first, second);
        assertEquals(32, first.length);
    }

    @Test
    void rejectsOFSAndWrongReportYearInsteadOfFallingBack() {
        var ofs = new OpenDartFinancialRow(RECEIPT, "2025", "11011", "00126380", "OFS",
                "IS", "display", "ifrs_Revenue", "Revenue", null,
                null, "ignored term name", "10", null, null, null, null, "1", "KRW");
        when(client.fetch("00126380", 2025)).thenReturn(success(ofs));
        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE,
                assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                        new OpenDartAnnualCfsRequest(CONTEXT, RECEIPT, FactPredicate.REVENUE)))
                        .category());
    }

    @Test
    void publicTypesDoNotExposeSecretsOrPersistenceRepositories() {
        assertFalse(OpenDartAnnualCfsRequest.class.getRecordComponents()[0]
                .getName().toLowerCase().contains("key"));
        assertTrue(List.of(OpenDartAnnualCfsAdapter.class.getDeclaredFields()).stream()
                .noneMatch(field -> field.getType().getPackageName().contains("repository")));
    }

    private static OpenDartFinancialResponse success(OpenDartFinancialRow... rows) {
        return new OpenDartFinancialResponse("000", "OK", List.of(rows));
    }

    private static OpenDartFinancialRow revenueRow(String amount) {
        return row("ifrs_Revenue", amount);
    }

    private static OpenDartFinancialRow row(String accountId, String amount) {
        return OpenDartWitnessFixtures.value("00126380", "20260331000123", accountId, amount);
    }

    private record StatusCase(String status, OpenDartProviderException.Category category) {}
}
