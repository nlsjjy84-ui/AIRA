package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named = "AIRA_OFFICIAL_MULTI_COMPANY_PREPARE", matches = "true")
class OfficialMultiCompanyPreparationPostgresTests {
    @Autowired OfficialCompanyDataPreparationOperation preparation;
    @Autowired JdbcTemplate jdbc;

    @Test
    void preparesTwoOfficialCompaniesThroughTheReusableIngestionPath() {
        var prepared = preparation.prepareByStockCodes(List.of("000660", "035420"), 2025);
        assertEquals(2, prepared.size());
        assertNotEquals(prepared.get(0).entityId(), prepared.get(1).entityId());
        for (var company : prepared) {
            assertEquals(2, jdbc.queryForObject("""
                    SELECT count(DISTINCT f.predicate) FROM fact f
                    JOIN fact_assertion fa ON fa.fact_id = f.id
                    JOIN evidence e ON e.id = fa.evidence_id
                    JOIN source s ON s.id = e.source_id
                    WHERE f.subject_entity_id = ? AND f.status = 'SUPPORTED'
                      AND f.predicate IN ('REVENUE', 'OPERATING_INCOME')
                      AND s.external_key = 'opendart' AND e.external_id IS NOT NULL
                    """, Integer.class, company.entityId()));
        }
    }
}
