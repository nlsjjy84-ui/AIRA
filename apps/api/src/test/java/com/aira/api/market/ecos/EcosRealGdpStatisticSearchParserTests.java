package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EcosRealGdpStatisticSearchParserTests {
    private final EcosRealGdpStatisticSearchParser parser =
            new EcosRealGdpStatisticSearchParser(new ObjectMapper());

    @Test
    void parsesConfirmedRealGdpObservationContract() {
        var page = parser.parse(raw(page(2,
                row("2026Q1", "596692.8"),
                row("2026Q2", "600474.9"))));

        assertEquals(2, page.totalCount());
        assertEquals(2, page.observations().size());
        var first = page.observations().getFirst();
        assertEquals("200Y104", first.statCode());
        assertEquals("1400", first.itemCode1());
        assertEquals("십억원", first.unitName());
        assertEquals("2026Q1", first.time());
        assertEquals("596692.8", first.rawDataValue());
        assertEquals(new BigDecimal("596692.8"), first.numericValue());
    }

    @Test
    void rejectsRootEnvelopeAndRowSchemaDrift() {
        assertMalformed("{\"RESULT\":{\"CODE\":\"ERROR-200\"}}");
        assertMalformed("{\"StatisticSearch\":{},\"extra\":{}}");
        assertMalformed("{\"StatisticSearch\":{\"list_total_count\":1}}");

        String withUnknownField = row("2026Q1", "596692.8")
                .replace("}", ",\"UNEXPECTED\":\"x\"}");
        assertMalformed(page(1, withUnknownField));

        String missingField = row("2026Q1", "596692.8")
                .replace("\"WGT\":null,", "");
        assertMalformed(page(1, missingField));
    }

    @Test
    void rejectsIdentityUnitDimensionWeightAndTimeDrift() {
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("200Y104", "200Y999")));
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("\"1400\"", "\"9999\"")));
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("십억원", "억원")));
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("\"ITEM_CODE2\":null", "\"ITEM_CODE2\":\"X\"")));
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("\"WGT\":null", "\"WGT\":\"1\"")));
        assertMalformed(page(1, row("2026Q5", "596692.8")));
    }

    @Test
    void preservesBlankAndNullDataValueWithoutNumericFactValue() {
        var blank = parser.parse(raw(page(1, row("2026Q1", ""))));
        assertEquals("", blank.observations().getFirst().rawDataValue());
        assertEquals(null, blank.observations().getFirst().numericValue());

        String nullValue = row("2026Q1", "596692.8")
                .replace("\"DATA_VALUE\":\"596692.8\"", "\"DATA_VALUE\":null");
        var nil = parser.parse(raw(page(1, nullValue)));
        assertEquals(null, nil.observations().getFirst().rawDataValue());
        assertEquals(null, nil.observations().getFirst().numericValue());
    }

    @Test
    void rejectsNonStringOrNonDecimalDataValue() {
        assertMalformed(page(1, row("2026Q1", "596692.8")
                .replace("\"DATA_VALUE\":\"596692.8\"", "\"DATA_VALUE\":596692.8")));
        assertMalformed(page(1, row("2026Q1", "596,692.8")));
        assertMalformed(page(1, row("2026Q1", "1E3")));
        assertMalformed(page(1, row("2026Q1", " 596692.8")));
    }

    @Test
    void collapsesIdenticalDuplicatesButRejectsConflictingDuplicates() {
        String first = row("2026Q1", "596692.8");
        var page = parser.parse(raw(page(2, first, first)));
        assertEquals(1, page.observations().size());

        assertMalformed(page(2,
                row("2026Q1", "596692.8"),
                row("2026Q1", "596693.0")));
    }

    @Test
    void rejectsImpossibleTotalCountAndNonIntegerTotal() {
        assertMalformed(page(0, row("2026Q1", "596692.8")));
        assertMalformed(page(1, row("2026Q1", "596692.8"))
                .replace("\"list_total_count\":1", "\"list_total_count\":1.0"));
    }

    private void assertMalformed(String body) {
        var error = assertThrows(EcosProviderException.class, () -> parser.parse(raw(body)));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
    }

    private static EcosRawResponse raw(String body) {
        return new EcosRawResponse(200, body);
    }

    private static String page(long totalCount, String... rows) {
        return "{\"StatisticSearch\":{\"list_total_count\":" + totalCount
                + ",\"row\":[" + String.join(",", rows) + "]}}";
    }

    private static String row(String time, String value) {
        return "{"
                + "\"STAT_CODE\":\"200Y104\","
                + "\"STAT_NAME\":\"2.1.2.1.2. 경제활동별 GDP 및 GNI(계절조정, 실질, 분기)\","
                + "\"ITEM_CODE1\":\"1400\","
                + "\"ITEM_NAME1\":\"국내총생산(시장가격, GDP)\","
                + "\"ITEM_CODE2\":null,\"ITEM_NAME2\":null,"
                + "\"ITEM_CODE3\":null,\"ITEM_NAME3\":null,"
                + "\"ITEM_CODE4\":null,\"ITEM_NAME4\":null,"
                + "\"UNIT_NAME\":\"십억원\",\"WGT\":null,"
                + "\"TIME\":\"" + time + "\","
                + "\"DATA_VALUE\":\"" + value + "\"}";
    }
}
