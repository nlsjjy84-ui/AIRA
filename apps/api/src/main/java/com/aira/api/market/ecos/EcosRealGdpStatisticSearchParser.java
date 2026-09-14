package com.aira.api.market.ecos;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class EcosRealGdpStatisticSearchParser {
    private static final Pattern QUARTER = Pattern.compile("[0-9]{4}Q[1-4]");
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final int ROW_FIELD_COUNT = 14;

    private final ObjectMapper objectMapper;

    public EcosRealGdpStatisticSearchParser(ObjectMapper objectMapper) {
        if (objectMapper == null) throw new IllegalArgumentException("objectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    public EcosStatisticSearchPage parse(EcosRawResponse raw) {
        if (raw.statusCode() != 200) throw malformed("StatisticSearch requires HTTP 200");
        JsonNode root = readRoot(raw.body());
        if (!root.isObject() || root.size() != 1 || root.get("StatisticSearch") == null) {
            throw malformed("StatisticSearch root schema is invalid");
        }
        JsonNode envelope = root.get("StatisticSearch");
        if (!envelope.isObject() || envelope.size() != 2
                || envelope.get("list_total_count") == null
                || envelope.get("row") == null) {
            throw malformed("StatisticSearch envelope schema is invalid");
        }

        JsonNode totalNode = envelope.get("list_total_count");
        if (!totalNode.isIntegralNumber()) {
            throw malformed("list_total_count must be an integer");
        }
        long totalCount = totalNode.longValue();
        if (totalCount < 0) throw malformed("list_total_count must not be negative");

        JsonNode rows = envelope.get("row");
        if (!rows.isArray()) throw malformed("row must be an array");
        if (rows.size() > totalCount) {
            throw malformed("row count exceeds list_total_count");
        }

        Map<ObservationKey, EcosStatisticSearchObservation> unique = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            EcosStatisticSearchObservation observation = parseRow(row);
            ObservationKey key = ObservationKey.from(observation);
            EcosStatisticSearchObservation existing = unique.putIfAbsent(key, observation);
            if (existing != null && !existing.equals(observation)) {
                throw malformed("conflicting duplicate StatisticSearch observation");
            }
        }
        return new EcosStatisticSearchPage(totalCount, List.copyOf(unique.values()));
    }

    private EcosStatisticSearchObservation parseRow(JsonNode row) {
        if (!row.isObject() || row.size() != ROW_FIELD_COUNT) {
            throw malformed("StatisticSearch row schema is invalid");
        }

        String statCode = requiredText(row, "STAT_CODE");
        String statName = requiredText(row, "STAT_NAME");
        String itemCode1 = requiredText(row, "ITEM_CODE1");
        String itemName1 = requiredText(row, "ITEM_NAME1");
        String itemCode2 = nullableText(row, "ITEM_CODE2");
        String itemName2 = nullableText(row, "ITEM_NAME2");
        String itemCode3 = nullableText(row, "ITEM_CODE3");
        String itemName3 = nullableText(row, "ITEM_NAME3");
        String itemCode4 = nullableText(row, "ITEM_CODE4");
        String itemName4 = nullableText(row, "ITEM_NAME4");
        String unitName = requiredText(row, "UNIT_NAME");
        String weight = nullableText(row, "WGT");
        String time = requiredText(row, "TIME");
        ParsedDataValue dataValue = dataValue(row);

        validateTarget(statCode, statName, itemCode1, itemName1,
                itemCode2, itemName2, itemCode3, itemName3,
                itemCode4, itemName4, unitName, weight, time);

        return new EcosStatisticSearchObservation(
                statCode, statName, itemCode1, itemName1,
                itemCode2, itemName2, itemCode3, itemName3,
                itemCode4, itemName4, unitName, weight,
                time, dataValue.raw(), dataValue.numeric());
    }

    private static ParsedDataValue dataValue(JsonNode row) {
        JsonNode value = row.get("DATA_VALUE");
        if (value == null) throw malformed("DATA_VALUE is missing");
        if (value.isNull()) return new ParsedDataValue(null, null);
        if (!value.isString()) throw malformed("DATA_VALUE must be text or null");
        String raw = value.stringValue();
        if (raw.isEmpty()) return new ParsedDataValue("", null);
        if (!raw.equals(raw.strip()) || !DECIMAL.matcher(raw).matches()) {
            throw malformed("DATA_VALUE must be a strict decimal string");
        }
        try {
            return new ParsedDataValue(raw, new BigDecimal(raw));
        } catch (NumberFormatException exception) {
            throw malformed("DATA_VALUE could not be parsed as decimal");
        }
    }

    private static void validateTarget(
            String statCode, String statName, String itemCode1, String itemName1,
            String itemCode2, String itemName2, String itemCode3, String itemName3,
            String itemCode4, String itemName4, String unitName, String weight, String time) {
        if (!EcosRealGdpContract.STAT_CODE.equals(statCode)
                || !EcosRealGdpContract.STAT_NAME.equals(statName)) {
            throw malformed("StatisticSearch series identity does not match REAL_GDP contract");
        }
        if (!EcosRealGdpContract.ITEM_CODE1.equals(itemCode1)
                || !EcosRealGdpContract.ITEM_NAME1.equals(itemName1)) {
            throw malformed("StatisticSearch item identity does not match REAL_GDP contract");
        }
        if (itemCode2 != null || itemName2 != null
                || itemCode3 != null || itemName3 != null
                || itemCode4 != null || itemName4 != null) {
            throw malformed("REAL_GDP contract requires ITEM_CODE2..4 and names to be null");
        }
        if (!EcosRealGdpContract.UNIT_NAME.equals(unitName)) {
            throw malformed("StatisticSearch unit does not match REAL_GDP contract");
        }
        if (weight != null) {
            throw malformed("REAL_GDP contract requires WGT to be null");
        }
        if (!QUARTER.matcher(time).matches()) {
            throw malformed("TIME must match YYYYQn for quarterly REAL_GDP");
        }
    }

    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isString()) {
            throw malformed(field + " must be a text value");
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

    private JsonNode readRoot(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw malformed("StatisticSearch response is not valid JSON");
        }
    }

    private static EcosProviderException malformed(String message) {
        return new EcosProviderException(EcosProviderException.Category.MALFORMED_RESPONSE, message);
    }

    private record ParsedDataValue(String raw, BigDecimal numeric) {
    }

    private record ObservationKey(
            String statCode, String itemCode1, String itemCode2,
            String itemCode3, String itemCode4, String time) {
        static ObservationKey from(EcosStatisticSearchObservation observation) {
            return new ObservationKey(
                    observation.statCode(),
                    observation.itemCode1(),
                    observation.itemCode2(),
                    observation.itemCode3(),
                    observation.itemCode4(),
                    observation.time());
        }
    }
}
