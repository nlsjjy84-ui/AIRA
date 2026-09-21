package com.aira.api.market.query;

import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.MarketIndexBoardResponse;
import com.aira.api.market.dto.MarketIndexBoardResponse.IndexView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketIndexQuery {
    private static final Set<String> PREDICATES = Set.of("INDEX_CLOSE", "INDEX_CHANGE", "INDEX_CHANGE_RATE");
    private final JdbcTemplate jdbc;

    public MarketIndexQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public MarketIndexBoardResponse latest() {
        return new MarketIndexBoardResponse(List.of(find("KOSPI"), find("KOSDAQ")));
    }

    private IndexView find(String marketCode) {
        String canonicalKey = "MARKET:KR:" + marketCode;
        LocalDate date = jdbc.query("""
                SELECT max(f.period_start)
                FROM fact f JOIN entity e ON e.id=f.subject_entity_id
                WHERE e.canonical_key=?
                  AND f.predicate IN ('INDEX_CLOSE','INDEX_CHANGE','INDEX_CHANGE_RATE')
                """, rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, canonicalKey);
        if (date == null) return empty(marketCode, CanonicalDataState.NO_DATA, "NO_INDEX_FACTS");

        List<Row> rows = jdbc.query("""
                SELECT f.predicate, f.status, f.value_number,
                       ev.id, ev.external_id, ev.original_url,
                       s.name, s.source_type, s.external_key
                FROM fact f
                JOIN entity ent ON ent.id=f.subject_entity_id
                LEFT JOIN fact_assertion fa ON fa.fact_id=f.id
                LEFT JOIN evidence ev ON ev.id=fa.evidence_id
                LEFT JOIN source s ON s.id=ev.source_id
                WHERE ent.canonical_key=? AND f.period_start=? AND f.period_end=?
                  AND f.predicate IN ('INDEX_CLOSE','INDEX_CHANGE','INDEX_CHANGE_RATE')
                ORDER BY f.predicate, ev.id
                """, (rs, rowNum) -> new Row(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9)), canonicalKey, date, date);
        if (rows.stream().anyMatch(row -> "CONFLICTING".equals(row.status())))
            return empty(marketCode, CanonicalDataState.CONFLICTING, "INDEX_FACT_CONFLICT");

        Map<String, Row> byPredicate = new HashMap<>();
        for (Row row : rows) {
            if (!PREDICATES.contains(row.predicate()) || row.value() == null || byPredicate.putIfAbsent(row.predicate(), row) != null)
                return empty(marketCode, CanonicalDataState.BLOCKED, "INDEX_FACT_AMBIGUOUS");
        }
        if (!byPredicate.keySet().containsAll(PREDICATES))
            return empty(marketCode, CanonicalDataState.PARTIAL, "INDEX_FACTS_INCOMPLETE");

        Set<UUID> evidenceIds = rows.stream().map(Row::evidenceId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (evidenceIds.size() != 1 || rows.stream().anyMatch(row -> row.evidenceId() == null
                || !"EXCHANGE".equals(row.sourceType()) || !"krx".equals(row.sourceExternalKey())))
            return empty(marketCode, CanonicalDataState.BLOCKED, "INDEX_EVIDENCE_INVALID");
        String apiId = marketCode.equals("KOSPI") ? "kospi_dd_trd" : "kosdaq_dd_trd";
        String expectedExternalId = "KRX_OPENAPI:" + apiId + ":" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        if (rows.stream().anyMatch(row -> !expectedExternalId.equals(row.evidenceExternalId())))
            return empty(marketCode, CanonicalDataState.BLOCKED, "INDEX_EVIDENCE_DATE_MISMATCH");

        Row close = byPredicate.get("INDEX_CLOSE");
        Row change = byPredicate.get("INDEX_CHANGE");
        Row rate = byPredicate.get("INDEX_CHANGE_RATE");
        return new IndexView(marketCode, CanonicalDataState.AVAILABLE, null, date,
                close.value(), change.value(), rate.value(), close.evidenceId(),
                close.sourceName(), close.originalUrl());
    }

    private static IndexView empty(String marketCode, CanonicalDataState state, String reason) {
        return new IndexView(marketCode, state, reason, null, null, null, null, null, null, null);
    }

    private record Row(String predicate, String status, BigDecimal value, UUID evidenceId,
            String evidenceExternalId, String originalUrl, String sourceName,
            String sourceType, String sourceExternalKey) {}
}
