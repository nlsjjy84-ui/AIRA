package com.aira.api.market.krx;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record KrxSnapshot(KrxDataset dataset, LocalDate date, List<Map<String, String>> rows, byte[] hash) {
    public KrxSnapshot {
        rows = List.copyOf(rows);
        hash = hash.clone();
    }
    @Override public byte[] hash() { return hash.clone(); }
    public String basDd() { return date.format(DateTimeFormatter.BASIC_ISO_DATE); }

    public static KrxSnapshot validated(KrxDataset dataset, LocalDate date, List<Map<String, String>> rows) {
        if (dataset == null || date == null || rows == null) throw new IllegalArgumentException("KRX dataset/date/rows are required");
        String basDd = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<String> canonicalRows = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (Map<String, String> row : rows) {
            if (row == null || !dataset.fields().containsAll(row.keySet())) throw new IllegalArgumentException("Unexpected KRX response field");
            if (dataset.dated() && !basDd.equals(row.get("BAS_DD"))) throw new IllegalArgumentException("KRX response date differs from request");
            String identity = dataset.stockBase() ? required(row, "ISU_CD") + "/" + required(row, "ISU_SRT_CD")
                    : dataset.stockDaily() ? required(row, "ISU_CD") : null;
            if (identity != null && !identities.add(identity)) throw new IllegalArgumentException("Duplicate KRX row identity");
            canonicalRows.add(serialize(dataset, row));
        }
        canonicalRows.sort(Comparator.naturalOrder());
        String content = "KRX_OPENAPI|" + dataset.apiId() + "|" + basDd + "\n" + String.join("\n", canonicalRows);
        try {
            return new KrxSnapshot(dataset, date, rows, MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String required(Map<String, String> row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("KRX identity field is missing: " + field);
        return value;
    }
    private static String serialize(KrxDataset dataset, Map<String, String> row) {
        StringBuilder out = new StringBuilder();
        for (String field : dataset.fields()) {
            String value = row.get(field);
            out.append(value == null ? "-1:" : value.length() + ":" + value);
        }
        return out.toString();
    }
}
