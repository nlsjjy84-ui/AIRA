package com.aira.api.stock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 회사 엔티티 id를 OpenDART 고유번호(corp_code)와 이름으로 바꾼다. 종목 화면의 출발점이다. */
@Repository
public class StockCompanyLookup {
    private final JdbcTemplate jdbc;

    public StockCompanyLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Company(UUID entityId, String name, String corpCode) {}

    public Optional<Company> find(UUID entityId) {
        List<Company> rows = jdbc.query("""
                SELECT e.id, e.canonical_name, x.identifier_value
                FROM entity e
                JOIN entity_external_identifier x ON x.entity_id = e.id
                     AND x.namespace = 'OPENDART' AND x.identifier_type = 'CORP_CODE'
                WHERE e.id = ? AND e.entity_type = 'COMPANY'
                """, (rs, i) -> new Company(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), entityId);
        return rows.stream().findFirst();
    }

    /** 같은 이름의 종목(SECURITY) 엔티티가 가진 6자리 단축코드. DART 회사정보를 못 받을 때만 쓰는 예비 경로. */
    public Optional<String> shortCodeByName(String name) {
        List<String> rows = jdbc.query("""
                SELECT symbol FROM entity
                WHERE entity_type = 'SECURITY' AND canonical_name = ? AND symbol IS NOT NULL
                ORDER BY symbol
                """, (rs, i) -> rs.getString(1), name);
        return rows.stream().filter(code -> code != null && code.matches("[0-9A-Z]{6}")).findFirst();
    }

    public java.util.Map<String, UUID> companyIdsByCorpCode(java.util.Collection<String> corpCodes) {
        java.util.Map<String, UUID> out = new java.util.HashMap<>();
        if (corpCodes.isEmpty()) return out;
        String marks = String.join(",", java.util.Collections.nCopies(corpCodes.size(), "?"));
        jdbc.query("SELECT x.identifier_value, x.entity_id FROM entity_external_identifier x JOIN entity e ON e.id=x.entity_id"
                + " AND e.entity_type='COMPANY' WHERE x.namespace='OPENDART' AND x.identifier_type='CORP_CODE'"
                + " AND x.identifier_value IN (" + marks + ")",
                rs -> { out.put(rs.getString(1), rs.getObject(2, UUID.class)); }, corpCodes.toArray());
        return out;
    }

    public List<String> allCorpCodes() {
        return jdbc.query("""
                SELECT DISTINCT x.identifier_value FROM entity_external_identifier x
                JOIN entity e ON e.id = x.entity_id AND e.entity_type = 'COMPANY'
                WHERE x.namespace = 'OPENDART' AND x.identifier_type = 'CORP_CODE'
                ORDER BY x.identifier_value
                """, (rs, i) -> rs.getString(1));
    }
}
