package com.aira.api.market.query;

import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.EntitySearchResponse;
import com.aira.api.market.dto.EntitySearchResponse.Item;
import com.aira.api.market.domain.EntityType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalEntitySearchQuery {
    private final JdbcTemplate jdbc;
    public CanonicalEntitySearchQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public EntitySearchResponse find(String term) {
        if (term == null || term.isBlank() || term.strip().length() > 100)
            return new EntitySearchResponse(CanonicalDataState.UNSUPPORTED, java.util.List.of());
        String stripped = term.strip();
        String escaped = stripped.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_");
        // Neutral deterministic identity lookup, not a popularity or investment ranking.
        // Names/aliases match anywhere in the text ("전자" finds "삼성전자"); the symbol must match exactly.
        // Order only reflects how strongly the text matches the identity (exact, then prefix, then contained),
        // which SEARCH_RANKING_NEUTRALITY_BOUNDARY_V1 allows as an exact/strong official-name match factor.
        var entities = jdbc.query("""
                SELECT e.id,e.entity_type,e.canonical_key,e.canonical_name,e.market_code,e.symbol,
                       (SELECT x.identifier_value FROM entity_external_identifier x
                        WHERE x.entity_id=e.id AND x.namespace='OPENDART' AND x.identifier_type='CORP_CODE'
                        ORDER BY x.created_at DESC, x.identifier_value LIMIT 1) AS external_identifier
                FROM entity e
                WHERE e.active=true AND e.entity_type IN ('COMPANY','SECURITY')
                  AND (
                    lower(e.canonical_name) LIKE lower(?) ESCAPE '\\'
                    OR lower(e.symbol)=lower(?)
                    OR EXISTS (
                        SELECT 1 FROM entity_alias a
                        WHERE a.entity_id = e.id AND lower(a.alias) LIKE lower(?) ESCAPE '\\'
                    )
                  )
                ORDER BY
                  CASE
                    WHEN lower(e.symbol)=lower(?) OR lower(e.canonical_name)=lower(?)
                         OR EXISTS (SELECT 1 FROM entity_alias a WHERE a.entity_id=e.id AND lower(a.alias)=lower(?)) THEN 0
                    WHEN lower(e.canonical_name) LIKE lower(?) ESCAPE '\\'
                         OR EXISTS (SELECT 1 FROM entity_alias a WHERE a.entity_id=e.id AND lower(a.alias) LIKE lower(?) ESCAPE '\\') THEN 1
                    ELSE 2
                  END,
                  e.entity_type,e.canonical_key LIMIT 50
                """, (rs, n) -> new Item(rs.getObject(1, java.util.UUID.class),
                EntityType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7)),
                "%" + escaped + "%", stripped, "%" + escaped + "%",
                stripped, stripped, stripped, escaped + "%", escaped + "%");
        return new EntitySearchResponse(entities.isEmpty() ? CanonicalDataState.NO_DATA
                : CanonicalDataState.AVAILABLE, entities);
    }
}
