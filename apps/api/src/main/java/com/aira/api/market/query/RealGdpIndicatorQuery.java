package com.aira.api.market.query;

import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.EconomicIndicatorResponse;
import com.aira.api.market.dto.EconomicIndicatorResponse.ObservationView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealGdpIndicatorQuery {
    private static final String BINDING = "StatisticSearch:200Y104:1400:-:-:-:Q";
    private final JdbcTemplate jdbc;

    public RealGdpIndicatorQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public EconomicIndicatorResponse latest() {
        List<Row> rows = jdbc.query("""
                SELECT f.period_start, f.period_end, f.status, f.value_number,
                       ev.id, ev.external_id, ev.status, s.name, s.source_type, s.external_key,
                       s.canonical_domain, s.active, ss.active, sm.active, sm.provider_binding_key,
                       sm.provider_series_name, sm.provider_item_name,
                       sm.provider_frequency_code, sm.provider_unit_name, sm.metadata_locator
                FROM fact f
                JOIN entity ent ON ent.id=f.subject_entity_id
                JOIN fact_statistical_context c ON c.fact_id=f.id
                JOIN statistical_series ss ON ss.id=c.statistical_series_id
                JOIN statistical_series_source_mapping sm ON sm.statistical_series_id=ss.id
                JOIN source s ON s.id=sm.source_id
                LEFT JOIN LATERAL (
                    SELECT evidence.id, evidence.external_id, evidence.status
                    FROM fact_assertion assertion
                    JOIN evidence ON evidence.id=assertion.evidence_id
                    WHERE assertion.fact_id=f.id AND evidence.status='AVAILABLE'
                    ORDER BY evidence.collected_at DESC, evidence.id DESC LIMIT 1
                ) ev ON true
                WHERE ent.canonical_key='KR' AND ent.entity_type='COUNTRY' AND ent.active=true
                  AND f.predicate='REAL_GDP' AND c.canonical_unit='KRW_BILLION'
                  AND ss.metric='REAL_GDP' AND ss.frequency='QUARTERLY'
                  AND ss.adjustment='SEASONALLY_ADJUSTED' AND ss.value_kind='LEVEL' AND ss.active=true
                  AND sm.active=true AND sm.provider_binding_key='StatisticSearch:200Y104:1400:-:-:-:Q'
                  AND s.active=true AND s.source_type='GOVERNMENT' AND s.external_key='BOK_ECOS'
                  AND f.period_start IN (SELECT period_start FROM fact
                      WHERE subject_entity_id=ent.id AND predicate='REAL_GDP'
                      ORDER BY period_start DESC LIMIT 2)
                ORDER BY f.period_start DESC, ev.id
                """, (rs, n) -> new Row(rs.getObject(1, LocalDate.class),
                        rs.getObject(2, LocalDate.class), rs.getString(3), rs.getBigDecimal(4),
                        rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                        rs.getBoolean(12), rs.getBoolean(13), rs.getBoolean(14), rs.getString(15),
                        rs.getString(16), rs.getString(17), rs.getString(18), rs.getString(19),
                        rs.getString(20)));
        if (rows.isEmpty()) return response(CanonicalDataState.NO_DATA, "NO_REAL_GDP_FACTS", List.of());
        if (rows.stream().anyMatch(row -> "CONFLICTING".equals(row.status())))
            return response(CanonicalDataState.CONFLICTING, "REAL_GDP_FACT_CONFLICT", List.of());

        Map<LocalDate, Row> byPeriod = new LinkedHashMap<>();
        for (Row row : rows) {
            if (!valid(row) || byPeriod.putIfAbsent(row.start(), row) != null)
                return response(CanonicalDataState.BLOCKED, "REAL_GDP_CONTRACT_INVALID", List.of());
        }
        List<ObservationView> observations = new ArrayList<>();
        for (Row row : byPeriod.values()) observations.add(new ObservationView(
                quarter(row.start()), row.value(), row.evidenceId()));
        CanonicalDataState state = observations.size() == 2
                ? CanonicalDataState.AVAILABLE : CanonicalDataState.PARTIAL;
        return response(state, state == CanonicalDataState.PARTIAL ? "REAL_GDP_HISTORY_INCOMPLETE" : null,
                List.copyOf(observations));
    }

    private static boolean valid(Row row) {
        return "SUPPORTED".equals(row.status()) && row.value() != null && row.evidenceId() != null
                && "AVAILABLE".equals(row.evidenceStatus())
                && row.start() != null && row.end() != null
                && row.end().equals(row.start().plusMonths(3).minusDays(1))
                && "GOVERNMENT".equals(row.sourceType()) && "BOK_ECOS".equals(row.sourceKey())
                && "Bank of Korea ECOS".equals(row.sourceName()) && "ecos.bok.or.kr".equals(row.domain())
                && row.sourceActive() && row.seriesActive() && row.mappingActive()
                && BINDING.equals(row.binding())
                && "2.1.2.1.2. 경제활동별 GDP 및 GNI(계절조정, 실질, 분기)".equals(row.seriesName())
                && "국내총생산(시장가격, GDP)".equals(row.itemName())
                && "Q".equals(row.frequency()) && "십억원".equals(row.unit())
                && "StatisticItemList/json/kr/200Y104/1/1000".equals(row.metadataLocator())
                && row.evidenceExternalId() != null
                && row.evidenceExternalId().startsWith("BOK_ECOS:StatisticSearch:v1:kr:200Y104:Q:");
    }

    private static String quarter(LocalDate start) {
        return start.getYear() + "Q" + ((start.getMonthValue() - 1) / 3 + 1);
    }

    private static EconomicIndicatorResponse response(CanonicalDataState state, String reason,
            List<ObservationView> observations) {
        return new EconomicIndicatorResponse(state, reason, "실질 국내총생산", "십억원",
                "한국은행 ECOS", observations);
    }

    private record Row(LocalDate start, LocalDate end, String status, BigDecimal value,
            UUID evidenceId, String evidenceExternalId, String evidenceStatus, String sourceName,
            String sourceType, String sourceKey, String domain, boolean sourceActive, boolean seriesActive,
            boolean mappingActive, String binding, String seriesName, String itemName,
            String frequency, String unit, String metadataLocator) {}
}
