package com.aira.api.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named = "AIRA_EVENT_ASSESSMENT_PREPARE", matches = "true")
class OfficialEventAssessmentPreparationPostgresTests {
    @Autowired OfficialEventAssessmentPreparationOperation preparation;
    @Autowired JdbcTemplate jdbc;

    @Test
    void preparesOneSharedAssessmentPerOfficialCompanyAndRemainsIdempotent() {
        var first = preparation.prepare();
        var second = preparation.prepare();
        assertEquals(3, first.size());
        assertEquals(first.stream().map(OfficialEventAssessmentPreparationOperation.PreparedAssessment::assessmentId).toList(),
                second.stream().map(OfficialEventAssessmentPreparationOperation.PreparedAssessment::assessmentId).toList());
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*) FROM assessment a
                WHERE a.analysis_version = 'official-annual-filing-v1'
                  AND a.status = 'COMPLETED'
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*) FROM assessment_evidence ae
                JOIN assessment a ON a.id = ae.assessment_id
                WHERE a.analysis_version = 'official-annual-filing-v1'
                """, Integer.class));
    }
}
