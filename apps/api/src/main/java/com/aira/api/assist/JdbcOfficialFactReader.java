package com.aira.api.assist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Plain read-only SQL over the stored official facts (fact, fact_assertion, evidence, entity). */
@Component
public class JdbcOfficialFactReader implements OfficialFactReader {
    private final JdbcTemplate jdbc;

    public JdbcOfficialFactReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public Resolved resolve(String name) {
        if (name == null || name.isBlank() || name.length() > 100) return new Resolved("NONE", null);
        List<Company> found = jdbc.query("""
                SELECT e.id, e.canonical_name FROM entity e
                WHERE e.active = true AND e.entity_type = 'COMPANY' AND lower(e.canonical_name) = lower(?)
                UNION
                SELECT e.id, e.canonical_name FROM entity e
                JOIN entity_alias a ON a.entity_id = e.id
                WHERE e.active = true AND e.entity_type = 'COMPANY' AND lower(a.alias) = lower(?)
                LIMIT 3
                """, (rs, n) -> new Company(rs.getObject(1, UUID.class), rs.getString(2)), name.strip(), name.strip());
        if (found.isEmpty()) return new Resolved("NONE", null);
        if (found.size() > 1) return new Resolved("MANY", null);
        return new Resolved("ONE", found.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public Fact find(UUID companyId, String predicate, Integer year) {
        StringBuilder sql = new StringBuilder("""
                SELECT f.value_number, f.currency_code, f.period_start, f.period_end, ev.external_id, ev.original_url
                FROM fact f
                JOIN fact_assertion fa ON fa.fact_id = f.id
                JOIN evidence ev ON ev.id = fa.evidence_id
                WHERE f.subject_entity_id = ? AND f.predicate = ? AND f.status = 'SUPPORTED'
                  AND f.value_number IS NOT NULL AND (f.period_end - f.period_start) >= 300
                """);
        if (year != null) sql.append(" AND EXTRACT(YEAR FROM f.period_end) = ").append(year.intValue()).append('\n');
        sql.append(" ORDER BY f.period_end DESC, ev.collected_at DESC LIMIT 1");
        List<Fact> rows = jdbc.query(sql.toString(), (rs, n) -> new Fact(rs.getBigDecimal(1), rs.getString(2),
                rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6)),
                companyId, predicate);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer latestYear(UUID companyId) {
        List<Integer> rows = jdbc.query("""
                SELECT CAST(EXTRACT(YEAR FROM max(f.period_end)) AS integer) FROM fact f
                WHERE f.subject_entity_id = ? AND f.status = 'SUPPORTED' AND f.value_number IS NOT NULL
                  AND (f.period_end - f.period_start) >= 300
                """, (rs, n) -> rs.getObject(1) == null ? null : rs.getInt(1), companyId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
