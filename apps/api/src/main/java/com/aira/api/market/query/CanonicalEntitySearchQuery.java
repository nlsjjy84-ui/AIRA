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
                SELECT id,entity_type,canonical_key,canonical_name,market_code,symbol
                FROM entity
                WHERE active=true AND entity_type IN ('COMPANY','SECURITY')
                  AND (lower(canonical_name) LIKE lower(?) ESCAPE '\\' OR lower(symbol)=lower(?))
                ORDER BY entity_type,canonical_key LIMIT 50
                """, (rs, n) -> new Item(rs.getObject(1, java.util.UUID.class),
                EntityType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6)), escaped + "%", term.strip());
        return new EntitySearchResponse(entities.isEmpty() ? CanonicalDataState.NO_DATA
                : CanonicalDataState.AVAILABLE, entities);
    }
}
