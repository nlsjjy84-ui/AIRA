package com.aira.api.market.query;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.KrxPreviousObservationResponse;
import com.aira.api.market.dto.KrxStoredSeriesResponse;
import com.aira.api.market.dto.KrxStoredSeriesResponse.Point;
import com.aira.api.market.repository.MarketEntityRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KrxStoredSeriesQuery {
    private static final Set<FactPredicate> APPROVED = Set.of(FactPredicate.OPEN_PRICE,
            FactPredicate.HIGH_PRICE, FactPredicate.LOW_PRICE, FactPredicate.CLOSE_PRICE,
            FactPredicate.TRADING_VOLUME, FactPredicate.TRADING_VALUE,
            FactPredicate.MARKET_CAP, FactPredicate.LISTED_SHARES);
    private final JdbcTemplate jdbc;
    private final MarketEntityRepository entities;
    public KrxStoredSeriesQuery(JdbcTemplate jdbc, MarketEntityRepository entities) {
        this.jdbc = jdbc; this.entities = entities;
    }

    @Transactional(readOnly = true)
    public KrxStoredSeriesResponse find(UUID securityId, FactPredicate predicate,
            LocalDate from, LocalDate to) {
        if (securityId == null || !APPROVED.contains(predicate) || from == null || to == null
                || from.isAfter(to)) return result(CanonicalDataState.UNSUPPORTED,
                "INVALID_RANGE_OR_PREDICATE", securityId, predicate, from, to, List.of());
        var security = entities.findById(securityId).orElse(null);
        if (security == null || security.getEntityType() != EntityType.SECURITY || !security.isActive()
                || !Set.of("KOSPI", "KOSDAQ").contains(security.getMarketCode()))
            return result(CanonicalDataState.UNSUPPORTED, "UNSUPPORTED_SECURITY",
                    securityId, predicate, from, to, List.of());
        String api = security.getMarketCode().equals("KOSPI") ? "stk_bydd_trd" : "ksq_bydd_trd";
        Integer conflicts = jdbc.queryForObject("""
                SELECT count(*) FROM fact
                WHERE subject_entity_id=? AND predicate=? AND period_start BETWEEN ? AND ?
                  AND period_end=period_start AND status<>'SUPPORTED'
                """, Integer.class, securityId, predicate.name(), from, to);
        if (conflicts != null && conflicts > 0) return result(CanonicalDataState.CONFLICTING,
                "NON_SUPPORTED_FACT_IN_RANGE", securityId, predicate, from, to, List.of());
        var rows = jdbc.query("""
                SELECT f.period_start,f.id,f.value_number,e.id,e.external_id
                FROM fact f JOIN fact_assertion fa ON fa.fact_id=f.id
                JOIN evidence e ON e.id=fa.evidence_id
                JOIN source s ON s.id=e.source_id
                WHERE f.subject_entity_id=? AND f.predicate=?
                  AND f.period_start BETWEEN ? AND ? AND f.period_end=f.period_start
                  AND f.status='SUPPORTED' AND f.value_number IS NOT NULL
                  AND fa.value_number=f.value_number
                  AND s.source_type='EXCHANGE' AND s.external_key='krx'
                  AND e.evidence_type='OFFICIAL_DATA' AND e.status IN ('ACTIVE','UPDATED')
                  AND e.external_id=('KRX_OPENAPI:' || ? || ':' || to_char(f.period_start,'YYYYMMDD'))
                ORDER BY f.period_start,f.id,e.id
                """, (rs, n) -> new Row(rs.getObject(1, LocalDate.class),
                rs.getObject(2, UUID.class), rs.getBigDecimal(3), rs.getObject(4, UUID.class),
                rs.getString(5)), securityId, predicate.name(), from, to, api);
        var grouped = new LinkedHashMap<LocalDate, List<Row>>();
        rows.forEach(row -> grouped.computeIfAbsent(row.date(), ignored -> new ArrayList<>()).add(row));
        List<Point> points = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            var sameDay = entry.getValue();
            if (sameDay.stream().map(Row::factId).distinct().count() != 1)
                return result(CanonicalDataState.CONFLICTING, "MULTIPLE_FACTS_ON_DATE",
                        securityId, predicate, from, to, List.of());
            Row first = sameDay.getFirst();
            points.add(new Point(first.date(), first.factId(), first.value(),
                    sameDay.stream().map(Row::evidenceId).distinct().toList(), first.externalId()));
        }
        return result(points.isEmpty() ? CanonicalDataState.NO_DATA : CanonicalDataState.AVAILABLE,
                points.isEmpty() ? "NO_STORED_OFFICIAL_OBSERVATIONS" : null,
                securityId, predicate, from, to, List.copyOf(points));
    }

    @Transactional(readOnly = true)
    public KrxPreviousObservationResponse previous(UUID securityId, FactPredicate predicate,
            LocalDate currentDate, UUID currentFactId) {
        if (currentDate == null || currentFactId == null) return new KrxPreviousObservationResponse(
                CanonicalDataState.UNSUPPORTED, "EXPLICIT_CURRENT_FACT_REQUIRED", securityId,
                predicate, currentDate, currentFactId, null, null, null, null, null);
        // The caller supplies D and its Fact ID; matching the exact stored observation prevents a prior point becoming Current.
        var series = find(securityId, predicate, LocalDate.of(1900, 1, 1), currentDate);
        if (series.state() != CanonicalDataState.AVAILABLE)
            return new KrxPreviousObservationResponse(series.state(), series.reason(), securityId,
                    predicate, currentDate, currentFactId, null, null, null, null, null);
        List<Point> points = series.points();
        Point current = points.getLast();
        if (!current.tradingDate().equals(currentDate) || !current.factId().equals(currentFactId))
            return new KrxPreviousObservationResponse(CanonicalDataState.NO_DATA,
                    "EXACT_D_FACT_MISSING", securityId, predicate, currentDate, currentFactId,
                    null, null, null, null, null);
        if (points.size() < 2) return new KrxPreviousObservationResponse(CanonicalDataState.NO_DATA,
                "PREVIOUS_OFFICIAL_OBSERVATION_MISSING", securityId, predicate, currentDate,
                currentFactId, current, null, null, null, null);
        Point previous = points.get(points.size() - 2);
        BigDecimal delta = current.value().subtract(previous.value());
        BigDecimal percent = previous.value().signum() > 0
                ? delta.multiply(new BigDecimal("100")).divide(previous.value(), 4, RoundingMode.HALF_UP) : null;
        return new KrxPreviousObservationResponse(CanonicalDataState.AVAILABLE, null, securityId,
                predicate, currentDate, currentFactId, current, previous, delta, percent,
                percent == null ? "BASE_NON_POSITIVE" : null);
    }

    private record Row(LocalDate date, UUID factId, BigDecimal value, UUID evidenceId, String externalId) {}
    private static KrxStoredSeriesResponse result(CanonicalDataState state, String reason,
            UUID id, FactPredicate predicate, LocalDate from, LocalDate to, List<Point> points) {
        return new KrxStoredSeriesResponse(state, reason, id, predicate, from, to, points);
    }
}
