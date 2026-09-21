package com.aira.api.market.krx;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record KrxPreparedPacket(KrxSnapshot base, KrxSnapshot daily,
        List<SecurityRow> securities, List<MarketValue> values) {
    public record SecurityRow(String standardCode, String shortCode, String name, String market) {}
    public record MarketValue(String standardCode, String shortCode, FactPredicate predicate,
            BigDecimal value, String field) {}

    private static final Map<String, FactPredicate> METRICS = Map.of(
            "TDD_OPNPRC", FactPredicate.OPEN_PRICE, "TDD_HGPRC", FactPredicate.HIGH_PRICE,
            "TDD_LWPRC", FactPredicate.LOW_PRICE, "TDD_CLSPRC", FactPredicate.CLOSE_PRICE,
            "ACC_TRDVOL", FactPredicate.TRADING_VOLUME, "ACC_TRDVAL", FactPredicate.TRADING_VALUE,
            "MKTCAP", FactPredicate.MARKET_CAP, "LIST_SHRS", FactPredicate.LISTED_SHARES);

    public static KrxPreparedPacket stock(KrxSnapshot base, KrxSnapshot daily) {
        if (base == null || daily == null || !base.dataset().stockBase() || !daily.dataset().stockDaily()
                || !base.date().equals(daily.date()) || !base.dataset().market().equals(daily.dataset().market())) {
            throw new IllegalArgumentException("Same-market, same-date KRX stock snapshots are required");
        }
        var byShort = new java.util.HashMap<String, SecurityRow>();
        var byStandard = new java.util.HashMap<String, SecurityRow>();
        for (var row : base.rows()) {
            String standard = required(row, "ISU_CD"), shortCode = required(row, "ISU_SRT_CD");
            var security = new SecurityRow(standard, shortCode, row.get("ISU_NM"), base.dataset().market());
            if (byShort.putIfAbsent(shortCode, security) != null || byStandard.putIfAbsent(standard, security) != null)
                throw new IllegalArgumentException("Ambiguous KRX base-info code mapping");
        }
        var securities = new java.util.ArrayList<SecurityRow>();
        var values = new java.util.ArrayList<MarketValue>();
        for (var row : daily.rows()) {
            String shortCode = required(row, "ISU_CD");
            var security = byShort.get(shortCode);
            if (security == null) throw new IllegalArgumentException("KRX daily short code has no same-date base mapping");
            securities.add(security);
            for (var metric : METRICS.entrySet()) {
                BigDecimal number = parseNumber(row.get(metric.getKey()), metric.getValue());
                if (number != null) values.add(new MarketValue(security.standardCode(), shortCode,
                        metric.getValue(), number, metric.getKey()));
            }
        }
        return new KrxPreparedPacket(base, daily, List.copyOf(securities), List.copyOf(values));
    }

    public KrxPreparedPacket selectShortCodes(Set<String> requestedShortCodes) {
        if (requestedShortCodes == null || requestedShortCodes.isEmpty()) {
            throw new IllegalArgumentException("At least one KRX short code is required");
        }
        var requested = new java.util.LinkedHashSet<String>();
        for (String raw : requestedShortCodes) {
            if (raw == null || !raw.trim().matches("[0-9]{6}")) {
                throw new IllegalArgumentException("KRX short code must be exactly six digits");
            }
            requested.add(raw.trim());
        }
        var selectedSecurities = securities.stream()
                .filter(security -> requested.contains(security.shortCode()))
                .toList();
        var found = selectedSecurities.stream().map(SecurityRow::shortCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!found.equals(Set.copyOf(requested))) {
            var missing = new java.util.LinkedHashSet<>(requested);
            missing.removeAll(found);
            throw new IllegalArgumentException("Requested KRX security is absent from exact snapshot: " + missing);
        }
        var standards = selectedSecurities.stream().map(SecurityRow::standardCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var selectedValues = values.stream()
                .filter(value -> standards.contains(value.standardCode()))
                .toList();
        return new KrxPreparedPacket(base, daily, List.copyOf(selectedSecurities), List.copyOf(selectedValues));
    }

    private static String required(Map<String, String> row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing KRX identity field: " + field);
        return value;
    }

    static BigDecimal parseNumber(String raw, FactPredicate predicate) {
        if (raw == null || raw.isBlank() || raw.trim().equals("-")) return null;
        String token = raw.trim();
        if (!token.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Malformed KRX metric: " + predicate);
        BigDecimal value = new BigDecimal(token);
        if ((predicate == FactPredicate.TRADING_VOLUME || predicate == FactPredicate.LISTED_SHARES)
                && value.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("Nonintegral KRX count metric");
        return value;
    }
}
