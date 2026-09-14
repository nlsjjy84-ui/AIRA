package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.domain.EventType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenDartDs005WaveTests {
    private final OpenDartDs005Parser parser = new OpenDartDs005Parser(new ObjectMapper());
    private final OffsetDateTime observed = OffsetDateTime.parse("2026-09-14T00:00:00Z");
    private OpenDartDs005Request request(String key) {
        return new OpenDartDs005Request(key, "00126380", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }
    private void wave(int number, int count, EventType type) {
        List<OpenDartDs005Catalog.Endpoint> endpoints = OpenDartDs005Catalog.automatic().stream()
                .filter(e -> e.wave() == number).toList();
        assertEquals(count, endpoints.size());
        for (var endpoint : endpoints) {
            var result = parser.parse(request(endpoint.key()), """
                    {"status":"000","message":"정상","list":[{"corp_code":"00126380","rcept_no":"20260101000001","value":"1"}]}
                    """, observed);
            assertEquals(1, result.receipts().size(), endpoint.key());
            var input = result.receipts().getFirst();
            assertEquals(type, input.eventType());
            assertEquals(endpoint.title(), input.neutralTitle());
            assertEquals("OPENDART_MATERIAL:" + endpoint.key() + ":20260101000001", input.structuredEvidence().externalId());
            assertNull(input.structuredEvidence().publishedAt());
            assertFalse(input.structuredEvidence().originalUrl().contains("?"));
        }
    }
    @Test void wave1() { wave(1, 4, EventType.DISCLOSURE); }
    @Test void wave2() { wave(2, 4, EventType.DISCLOSURE); }
    @Test void wave3() { wave(3, 4, EventType.DISCLOSURE); }
    @Test void wave4() { wave(4, 7, EventType.RISK); }
    @Test void wave5() { wave(5, 8, EventType.BUSINESS); }
    @Test void wave6() { wave(6, 4, EventType.BUSINESS); }
    @Test void wave7() { wave(7, 4, EventType.MARKET); }
    @Test void checksumAndHold() {
        assertEquals(35, OpenDartDs005Catalog.automatic().size());
        assertEquals(35, OpenDartDs005Catalog.automatic().stream().map(OpenDartDs005Catalog.Endpoint::key).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> request(OpenDartDs005Catalog.HOLD));
    }
    @Test void receiptHashOrderAndWindowIndependence() {
        var a = parser.parse(request("dfOcr"), """
                {"status":"000","message":"A","list":[{"rcept_no":"20260101000001","corp_code":"00126380","x":"a"},{"corp_code":"00126380","x":"b","rcept_no":"20260101000001"}]}
                """, observed).receipts().getFirst();
        var b = parser.parse(new OpenDartDs005Request("dfOcr", "00126380", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 1)), """
                {"status":"000","message":"B","list":[{"x":"b","corp_code":"00126380","rcept_no":"20260101000001"},{"x":"a","rcept_no":"20260101000001","corp_code":"00126380"}]}
                """, observed).receipts().getFirst();
        assertArrayEquals(a.structuredEvidence().contentHash(), b.structuredEvidence().contentHash());
        var changed = parser.parse(request("dfOcr"), """
                {"status":"000","message":"A","list":[{"rcept_no":"20260101000001","corp_code":"00126380","x":"c"}]}
                """, observed).receipts().getFirst();
        assertFalse(java.util.Arrays.equals(a.structuredEvidence().contentHash(), changed.structuredEvidence().contentHash()));
    }
    @Test void rejectsBadReceiptWithoutLosingOtherReceipt() {
        var result = parser.parse(request("dfOcr"), """
                {"status":"000","message":"A","list":[{"rcept_no":"20260101000001","corp_code":"99999999"},{"rcept_no":"20260101000002","corp_code":"00126380"}]}
                """, observed);
        assertEquals(1, result.receipts().size());
        assertEquals(1, result.rejectedReceipts().size());
    }
    @Test void noDataAndStatusClassification() {
        assertTrue(parser.parse(request("dfOcr"), "{" + "\"status\":\"013\",\"message\":\"no data\"}", observed).receipts().isEmpty());
        var error = assertThrows(OpenDartProviderException.class, () -> parser.parse(request("dfOcr"),
                "{" + "\"status\":\"010\",\"message\":\"bad key\"}", observed));
        assertEquals(OpenDartProviderException.Category.AUTHENTICATION, error.category());
    }
}
