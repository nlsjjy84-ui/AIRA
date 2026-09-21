package com.aira.api.market.krx;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record KrxIndexPacket(KrxSnapshot snapshot, String market, List<IndexValue> values) {
    public record IndexValue(FactPredicate predicate, BigDecimal value, String field) {}

    private static final Map<String, FactPredicate> METRICS = Map.of(
            "CLSPRC_IDX", FactPredicate.INDEX_CLOSE,
            "CMPPREVDD_IDX", FactPredicate.INDEX_CHANGE,
            "FLUC_RT", FactPredicate.INDEX_CHANGE_RATE);

    public static KrxIndexPacket from(KrxSnapshot snapshot) {
        if (snapshot == null || (snapshot.dataset() != KrxDataset.KOSPI_INDEX
                && snapshot.dataset() != KrxDataset.KOSDAQ_INDEX)) {
            throw new IllegalArgumentException("KRX index snapshot is required");
        }
        String market = snapshot.dataset().market();
        Set<String> names = market.equals("KOSPI") ? Set.of("KOSPI", "코스피") : Set.of("KOSDAQ", "코스닥");
        var matches = snapshot.rows().stream().filter(row -> {
            String name = row.get("IDX_NM");
            return name != null && names.contains(name.trim());
        }).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Exact representative KRX index row is required");
        var row = matches.getFirst();
        var values = METRICS.entrySet().stream().map(metric -> {
            BigDecimal value = parseSignedNumber(row.get(metric.getKey()), metric.getValue());
            if (value == null) throw new IllegalArgumentException("Representative KRX index metric is missing: " + metric.getKey());
            if (metric.getValue() == FactPredicate.INDEX_CLOSE && value.signum() < 0)
                throw new IllegalArgumentException("KRX index close cannot be negative");
            return new IndexValue(metric.getValue(), value, metric.getKey());
        }).toList();
        return new KrxIndexPacket(snapshot, market, values);
    }

    static BigDecimal parseSignedNumber(String raw, FactPredicate predicate) {
        if (raw == null || raw.isBlank() || raw.trim().equals("-")) return null;
        String token = raw.trim();
        if (!token.matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("Malformed KRX index metric: " + predicate);
        return new BigDecimal(token);
    }
}
