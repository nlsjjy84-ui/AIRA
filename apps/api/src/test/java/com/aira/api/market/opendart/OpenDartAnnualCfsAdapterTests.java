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

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.ingestion.EarningsIngestionBoundary;
import com.aira.api.market.ingestion.IngestionReceipt;
import com.aira.api.market.ingestion.SourceAwareEarningsIngestionInput;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenDartAnnualCfsAdapterTests {
    @Mock private OpenDartAnnualCfsClient client;
    @Mock private EntityExternalIdentifierRegistryService identifiers;
    @Mock private EarningsIngestionBoundary boundary;

    private OpenDartAnnualCfsAdapter adapter;
    private UUID entityId;
    private IngestionReceipt receipt;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new OpenDartAnnualCfsAdapter(client, identifiers, boundary,
                Clock.fixed(Instant.parse("2026-03-31T01:02:03Z"), ZoneOffset.UTC));
        entityId = UUID.randomUUID();
        receipt = new IngestionReceipt(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        lenient().when(identifiers.findEntity(any())).thenReturn(Optional.of(company(entityId)));
        lenient().when(boundary.ingest(any())).thenReturn(receipt);
    }

    @Test
    void ingestsAnnualCfsRevenueThroughBoundary() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("1,234.50")));

        assertEquals(receipt, adapter.ingest(
                new OpenDartAnnualCfsRequest("00126380", 2025, FactPredicate.REVENUE)));

        verify(client).fetch("00126380", 2025);
        verify(identifiers).findEntity(new ExternalIdentifierKey(
                "OPENDART", "CORP_CODE", "00126380"));
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        var input = captor.getValue();
        assertEquals(entityId, input.subjectEntityId());
        assertEquals(new BigDecimal("1234.50"), input.numberValue());
        assertEquals("KRW", input.currencyCode());
        assertEquals("2025-01-01", input.periodStart().toString());
        assertEquals("2025-12-31", input.periodEnd().toString());
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

        adapter.ingest(new OpenDartAnnualCfsRequest(
                "00126380", 2025, FactPredicate.OPERATING_INCOME));

        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        assertEquals(FactPredicate.OPERATING_INCOME, captor.getValue().predicate());
    }

    @Test
    void neverInfersUnsupportedAccountFromAccountName() {
        var unsupported = new OpenDartFinancialRow("20260331000123", "2025", "11011",
                "CFS", "IS", "custom_Revenue", "Revenue", "2025.01.01 ~ 2025.12.31",
                "100", "KRW");
        when(client.fetch("00126380", 2025)).thenReturn(success(unsupported));

        var failure = assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest("00126380", 2025, FactPredicate.REVENUE)));

        assertEquals(OpenDartProviderException.Category.NO_DATA, failure.category());
        verify(boundary, never()).ingest(any());
    }

    @Test
    void rejectsUnmappedCompanyWithoutNameOrStockFallback() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("100")));
        when(identifiers.findEntity(any())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> adapter.ingest(
                new OpenDartAnnualCfsRequest("00126380", 2025, FactPredicate.REVENUE)));
        verify(boundary, never()).ingest(any());
    }

    @Test
    void preservesLeadingZeroAndRejectsInvalidCorpCodes() {
        var request = new OpenDartAnnualCfsRequest(
                "00126380", 2025, FactPredicate.REVENUE);
        assertEquals("00126380", request.corpCode());
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartAnnualCfsRequest("126380", 2025, FactPredicate.REVENUE));
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
                    new OpenDartAnnualCfsRequest("00126380", 2025, FactPredicate.REVENUE)));
            assertEquals(entry.category(), failure.category());
        }
        verify(boundary, never()).ingest(any());
    }

    @Test
    void deterministicContentHashIgnoresCollectionTimeAndAccountName() {
        when(client.fetch("00126380", 2025)).thenReturn(success(revenueRow("1,000")));
        adapter.ingest(new OpenDartAnnualCfsRequest(
                "00126380", 2025, FactPredicate.REVENUE));
        var captor = ArgumentCaptor.forClass(SourceAwareEarningsIngestionInput.class);
        verify(boundary).ingest(captor.capture());
        byte[] first = captor.getValue().evidence().contentHash();

        var changedName = new OpenDartFinancialRow("20260331000123", "2025", "11011",
                "CFS", "IS", "ifrs_Revenue", "changed display label",
                "2025.01.01 ~ 2025.12.31", "1000.0", "KRW");
        when(client.fetch("00126380", 2025)).thenReturn(success(changedName));
        adapter.ingest(new OpenDartAnnualCfsRequest(
                "00126380", 2025, FactPredicate.REVENUE));
        verify(boundary, org.mockito.Mockito.times(2)).ingest(captor.capture());
        byte[] second = captor.getAllValues().getLast().evidence().contentHash();

        assertArrayEquals(first, second);
        assertEquals(32, first.length);
    }

    @Test
    void rejectsOFSAndWrongReportYearInsteadOfFallingBack() {
        var ofs = new OpenDartFinancialRow("20260331000123", "2025", "11011", "OFS",
                "IS", "ifrs_Revenue", "Revenue", "2025.01.01 ~ 2025.12.31", "10", "KRW");
        when(client.fetch("00126380", 2025)).thenReturn(success(ofs));
        assertEquals(OpenDartProviderException.Category.NO_DATA,
                assertThrows(OpenDartProviderException.class, () -> adapter.ingest(
                        new OpenDartAnnualCfsRequest("00126380", 2025, FactPredicate.REVENUE)))
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
        return new OpenDartFinancialRow("20260331000123", "2025", "11011", "CFS", "IS",
                accountId, "display label", "2025.01.01 ~ 2025.12.31", amount, "KRW");
    }

    private static MarketEntity company(UUID id) throws Exception {
        var constructor = MarketEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var entity = constructor.newInstance();
        set(entity, "id", id);
        set(entity, "entityType", EntityType.COMPANY);
        return entity;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record StatusCase(String status, OpenDartProviderException.Category category) {}
}
