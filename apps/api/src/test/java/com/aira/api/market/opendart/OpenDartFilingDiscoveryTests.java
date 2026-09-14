package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenDartFilingDiscoveryTests {
    private static final String CORP = "00126380";

    @Test
    void traversesAllPagesDeduplicatesOverlapPreservesMetadataAndSorts() {
        AtomicInteger calls = new AtomicInteger();
        OpenDartFilingPageClient client = (request, pageNumber) -> {
            calls.incrementAndGet();
            if (pageNumber == 1) {
                return page(1, 3, 2, List.of(
                        row("20250102000002", "20250102", "[기재정정] 사업보고서", "정"),
                        row("20250101000003", "20250101", "사업보고서", "")));
            }
            return page(2, 3, 2, List.of(
                    row("20250102000002", "20250102", "[기재정정] 사업보고서", "정"),
                    row("20250101000001", "20250101", "사업보고서", "철")));
        };

        var results = new OpenDartFilingDiscovery(client).discover(request());

        assertEquals(2, calls.get());
        assertEquals(List.of("20250101000001", "20250101000003", "20250102000002"),
                results.stream().map(OpenDartFilingCandidate::receiptNumber).toList());
        assertEquals("[기재정정] 사업보고서", results.get(2).reportName());
        assertEquals("정", results.get(2).remarks());
        assertEquals("철", results.get(0).remarks());
    }

    @Test
    void rejectsConflictingMetadataForSameReceipt() {
        OpenDartFilingPageClient client = (request, pageNumber) -> page(1, 2, 1, List.of(
                row("20250102000002", "20250102", "사업보고서", ""),
                row("20250102000002", "20250102", "[기재정정] 사업보고서", "정")));

        var error = assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(client).discover(request()));
        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE, error.category());
    }

    @Test
    void rejectsCompanyMismatchAndMalformedIdentityFields() {
        assertMalformed(rowForCorp("00999999", "20250102000002", "20250102"));
        assertMalformed(rowForCorp(CORP, "123", "20250102"));
        assertMalformed(rowForCorp(CORP, "20250102000002", "20250230"));
    }

    @Test
    void rejectsChangedPaginationTotalsAndIncompleteIntermediatePage() {
        OpenDartFilingPageClient changedTotals = (request, pageNumber) -> pageNumber == 1
                ? page(1, 2, 2, List.of(row("20250101000001", "20250101", "사업보고서", "")))
                : page(2, 3, 2, List.of(row("20250102000002", "20250102", "사업보고서", "")));
        assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(changedTotals).discover(request()));

        OpenDartFilingPageClient emptyMiddle = (request, pageNumber) -> pageNumber == 1
                ? page(1, 2, 2, List.of())
                : page(2, 2, 2, List.of(row("20250102000002", "20250102", "사업보고서", "")));
        assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(emptyMiddle).discover(request()));
    }

    @Test
    void rejectsTraversalWhoseUniqueReceiptCountDoesNotMatchProviderTotal() {
        OpenDartFilingPageClient client = (request, pageNumber) -> page(1, 2, 1, List.of(
                row("20250101000001", "20250101", "사업보고서", "")));

        assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(client).discover(request()));
    }

    @Test
    void treatsNoDataOnlyAsAnInitialEmptyResult() {
        OpenDartFilingPageClient noData = (request, pageNumber) -> new OpenDartFilingPageResponse(
                "013", "조회된 데이타가 없습니다.", null, null, null, null, null);
        assertTrue(new OpenDartFilingDiscovery(noData).discover(request()).isEmpty());

        OpenDartFilingPageClient lateNoData = (request, pageNumber) -> pageNumber == 1
                ? page(1, 2, 2, List.of(row("20250101000001", "20250101", "사업보고서", "")))
                : new OpenDartFilingPageResponse("013", "조회된 데이타가 없습니다.", null, null, null, null, null);
        assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(lateNoData).discover(request()));
    }

    @Test
    void validatesCallerScopeWithoutInventingDefaults() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartFilingDiscoveryRequest("126380", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartFilingDiscoveryRequest(CORP,
                        LocalDate.of(2025, 12, 31), LocalDate.of(2025, 1, 1), null, null));

        var request = new OpenDartFilingDiscoveryRequest(CORP, null, null, "  A  ", " ");
        assertNull(request.beginningDate());
        assertNull(request.endingDate());
        assertEquals("A", request.disclosureType());
        assertNull(request.disclosureDetailType());
    }

    private static void assertMalformed(OpenDartFilingTransportRow row) {
        OpenDartFilingPageClient client = (request, pageNumber) -> page(1, 1, 1, List.of(row));
        var error = assertThrows(OpenDartProviderException.class,
                () -> new OpenDartFilingDiscovery(client).discover(request()));
        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE, error.category());
    }

    private static OpenDartFilingDiscoveryRequest request() {
        return new OpenDartFilingDiscoveryRequest(CORP, null, null, null, null);
    }

    private static OpenDartFilingPageResponse page(
            int pageNumber, int totalCount, int totalPages, List<OpenDartFilingTransportRow> rows) {
        return new OpenDartFilingPageResponse(
                "000", "정상", pageNumber, 100, totalCount, totalPages, rows);
    }

    private static OpenDartFilingTransportRow row(
            String receipt, String date, String reportName, String remarks) {
        return new OpenDartFilingTransportRow(
                "Y", CORP, "삼성전자", "005930", reportName, receipt, "삼성전자", date, remarks);
    }

    private static OpenDartFilingTransportRow rowForCorp(
            String corpCode, String receipt, String date) {
        return new OpenDartFilingTransportRow(
                "Y", corpCode, "삼성전자", "005930", "사업보고서", receipt, "삼성전자", date, "");
    }
}
