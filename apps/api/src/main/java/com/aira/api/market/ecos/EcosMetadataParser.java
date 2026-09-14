package com.aira.api.market.ecos;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class EcosMetadataParser {
    private static final int ROW_FIELD_COUNT = 14;
    private final ObjectMapper objectMapper;

    public EcosMetadataParser(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    public EcosMetadataPage parse(EcosRawResponse raw, String expectedStatCode) {
        if (raw == null) throw new IllegalArgumentException("raw must not be null");
        if (expectedStatCode == null || expectedStatCode.isBlank()) {
            throw new IllegalArgumentException("expectedStatCode must not be blank");
        }
        if (raw.statusCode() != 200) {
            throw malformed("StatisticItemList requires HTTP 200");
        }
        JsonNode root = readRoot(raw.body());
        if (!root.isObject() || root.size() != 1 || root.get("StatisticItemList") == null) {
            throw malformed("StatisticItemList root schema is invalid");
        }
        JsonNode envelope = root.get("StatisticItemList");
        if (!envelope.isObject() || envelope.size() != 2
                || envelope.get("list_total_count") == null
                || envelope.get("row") == null) {
            throw malformed("StatisticItemList envelope schema is invalid");
        }

        long totalCount = totalCount(envelope.get("list_total_count"));
        JsonNode rows = envelope.get("row");
        if (!rows.isArray()) throw malformed("row must be an array");
        if (rows.size() > totalCount) {
            throw malformed("row count exceeds list_total_count");
        }

        List<EcosMetadataItem> items = new ArrayList<>();
        for (JsonNode row : rows) {
            items.add(parseRow(row, expectedStatCode));
        }
        return new EcosMetadataPage(totalCount, items);
    }

    private static long totalCount(JsonNode node) {
        if (!node.isIntegralNumber()) {
            throw malformed("list_total_count must be an integer");
        }
        long value = node.longValue();
        if (value < 0) throw malformed("list_total_count must not be negative");
        return value;
    }
    private static EcosMetadataItem parseRow(JsonNode row, String expectedStatCode) {
        if (!row.isObject() || row.size() != ROW_FIELD_COUNT) {
            throw malformed("StatisticItemList row schema is invalid");
        }

        String statCode = requiredText(row, "STAT_CODE");
        if (!expectedStatCode.equals(statCode)) {
            throw malformed("StatisticItemList STAT_CODE does not match request");
        }
        String statName = requiredText(row, "STAT_NAME");
        String groupCode = requiredText(row, "GRP_CODE");
        String groupName = requiredText(row, "GRP_NAME");
        String itemCode = requiredText(row, "ITEM_CODE");
        String itemName = requiredText(row, "ITEM_NAME");
        String parentCode = nullableText(row, "P_ITEM_CODE");
        String parentName = nullableText(row, "P_ITEM_NAME");
        if ((parentCode == null) != (parentName == null)) {
            throw malformed("P_ITEM_CODE and P_ITEM_NAME must both be null or both be present");
        }
        String cycle = requiredText(row, "CYCLE");
        String startTime = requiredText(row, "START_TIME");
        String endTime = requiredText(row, "END_TIME");
        long dataCount = nonNegativeInteger(row, "DATA_CNT");
        String unitName = nullableText(row, "UNIT_NAME");
        String weight = nullableText(row, "WEIGHT");
        return new EcosMetadataItem(
                statCode, statName, groupCode, groupName,
                itemCode, itemName, parentCode, parentName,
                cycle, startTime, endTime, dataCount, unitName, weight);
    }

    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isString()) {
            throw malformed(field + " must be text");
        }
        String text = value.stringValue();
        if (text.isBlank() || !text.equals(text.strip())) {
            throw malformed(field + " must be non-blank text without surrounding whitespace");
        }
        return text;
    }

    private static String nullableText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null) throw malformed(field + " is missing");
        if (value.isNull()) return null;
        if (!value.isString()) throw malformed(field + " must be text or null");
        String text = value.stringValue();
        if (text.isBlank() || !text.equals(text.strip())) {
            throw malformed(field + " must be non-blank text without surrounding whitespace");
        }
        return text;
    }
    private static long nonNegativeInteger(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw malformed(field + " must be an integer");
        }
        long parsed = value.longValue();
        if (parsed < 0) throw malformed(field + " must not be negative");
        return parsed;
    }

    private JsonNode readRoot(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw malformed("StatisticItemList response is not valid JSON");
        }
    }

    private static EcosProviderException malformed(String message) {
        return new EcosProviderException(
                EcosProviderException.Category.MALFORMED_RESPONSE, message);
    }
}
