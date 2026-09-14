package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EcosMetadataParserTests {
    private final EcosMetadataParser parser = new EcosMetadataParser(new ObjectMapper());

    @Test
    void parsesStrictOfficialShapedMetadataRow() {
        EcosMetadataPage page = parser.parse(raw(body(1, row("200Y104", "1400"))), "200Y104");

        assertEquals(1, page.totalCount());
        assertEquals(1, page.items().size());
        EcosMetadataItem item = page.items().getFirst();
        assertEquals("200Y104", item.statCode());
        assertEquals("1400", item.itemCode());
        assertEquals(266, item.dataCount());
        assertEquals("십억원", item.unitName());
        assertNull(item.weight());
        assertNull(item.parentItemCode());
    }
    @Test
    void rejectsWrongStatCodeAndUnexpectedSchema() {
        var mismatch = assertThrows(EcosProviderException.class,
                () -> parser.parse(raw(body(1, row("OTHER", "1400"))), "200Y104"));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, mismatch.category());

        String extraRoot = "{\"StatisticItemList\":{"
                + "\"list_total_count\":0,\"row\":[]},\"extra\":1}";
        assertThrows(EcosProviderException.class,
                () -> parser.parse(raw(extraRoot), "200Y104"));

        String extraRow = row("200Y104", "1400")
                .replace("}", ",\"EXTRA\":\"x\"}");
        assertThrows(EcosProviderException.class,
                () -> parser.parse(raw(body(1, extraRow)), "200Y104"));
    }

    @Test
    void rejectsInvalidCountAndParentPairing() {
        String wrongCountType = body(1, row("200Y104", "1400"))
                .replace("\"DATA_CNT\":266", "\"DATA_CNT\":\"266\"");
        assertThrows(EcosProviderException.class,
                () -> parser.parse(raw(wrongCountType), "200Y104"));

        String brokenParent = row("200Y104", "1400")
                .replace("\"P_ITEM_CODE\":null", "\"P_ITEM_CODE\":\"1000\"");
        assertThrows(EcosProviderException.class,
                () -> parser.parse(raw(body(1, brokenParent)), "200Y104"));
    }
    private static EcosRawResponse raw(String body) {
        return new EcosRawResponse(200, body);
    }

    private static String body(long total, String rows) {
        return "{\"StatisticItemList\":{"
                + "\"list_total_count\":" + total + ","
                + "\"row\":[" + rows + "]}}";
    }

    private static String row(String statCode, String itemCode) {
        return "{"
                + "\"STAT_CODE\":\"" + statCode + "\","
                + "\"STAT_NAME\":\"경제활동별 GDP 및 GNI(계절조정, 실질, 분기)\","
                + "\"GRP_CODE\":\"Group1\",\"GRP_NAME\":\"계정항목\","
                + "\"ITEM_CODE\":\"" + itemCode + "\","
                + "\"ITEM_NAME\":\"국내총생산(시장가격, GDP)\","
                + "\"P_ITEM_CODE\":null,\"P_ITEM_NAME\":null,"
                + "\"CYCLE\":\"Q\",\"START_TIME\":\"1960Q1\","
                + "\"END_TIME\":\"2026Q2\",\"DATA_CNT\":266,"
                + "\"UNIT_NAME\":\"십억원\",\"WEIGHT\":null}";
    }
}
