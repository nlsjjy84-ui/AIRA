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
        String escaped = term.strip().replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_");
        // Neutral deterministic identity lookup, not a relevance or investment ranking.
        var entities = jdbc.query("""
                SELECT e.id,e.entity_type,e.canonical_key,e.canonical_name,e.market_code,e.symbol,
                       (SELECT x.identifier_value FROM entity_external_identifier x
                        WHERE x.entity_id=e.id AND x.namespace='OPENDART' AND x.identifier_type='CORP_CODE'
                        ORDER BY x.created_at DESC, x.identifier_value LIMIT 1) AS external_identifier
                FROM entity e
                WHERE e.active=true AND e.entity_type IN ('COMPANY','SECURITY')
                  AND (lower(e.canonical_name) LIKE lower(?) ESCAPE '\\' OR lower(e.symbol)=lower(?))
                ORDER BY e.entity_type,e.canonical_key LIMIT 50
                """, (rs, n) -> new Item(rs.getObject(1, java.util.UUID.class),
                EntityType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7)), escaped + "%", term.strip());
        return new EntitySearchResponse(entities.isEmpty() ? CanonicalDataState.NO_DATA
                : CanonicalDataState.AVAILABLE, entities);
    }
}
