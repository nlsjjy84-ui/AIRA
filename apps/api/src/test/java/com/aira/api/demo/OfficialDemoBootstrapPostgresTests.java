package com.aira.api.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.analysis.query.CompanyEventExperienceQuery;
import com.aira.api.delivery.service.InAppAlertService;
import com.aira.api.delivery.service.PersonalBriefingService;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "AIRA_OFFICIAL_DEMO_BOOTSTRAP_E2E", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENDART_API_KEY", matches = ".+")
class OfficialDemoBootstrapPostgresTests {
    @Autowired OfficialDemoBootstrapOperation bootstrap;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyEventExperienceQuery companyEvents;
    @Autowired PersonalBriefingService briefings;
    @Autowired InAppAlertService alerts;

    @Test
    void preparesFreshOfficialProductPathAndRerunsIdempotently() {
        var first = bootstrap.prepare(2025);
        var second = bootstrap.prepare(2025);

        assertEquals(2, first.companies().size());
        assertEquals(2, first.eventCount());
        assertEquals(2, first.assessmentCount());
        assertEquals(companyIds(first), companyIds(second));
        assertEquals(first.eventCount(), second.eventCount());
        assertEquals(first.assessmentCount(), second.assessmentCount());

        for (UUID companyId : companyIds(first)) {
            assertEquals(2, count("""
                    SELECT count(DISTINCT f.id) FROM fact f
                    JOIN fact_assertion fa ON fa.fact_id=f.id
                    JOIN evidence e ON e.id=fa.evidence_id
                    JOIN source s ON s.id=e.source_id
                    WHERE f.subject_entity_id=? AND f.status='SUPPORTED'
                      AND f.predicate IN ('REVENUE','OPERATING_INCOME')
                      AND s.external_key='opendart' AND e.external_id IS NOT NULL
                    """, companyId));
            assertEquals(1, count("""
                    SELECT count(DISTINCT ev.id) FROM event ev
                    JOIN event_entity ee ON ee.event_id=ev.id
                    JOIN event_evidence eve ON eve.event_id=ev.id
                    JOIN evidence e ON e.id=eve.evidence_id
                    WHERE ee.entity_id=? AND ev.event_type='EARNINGS'
                      AND e.status='ACTIVE'
                    """, companyId));
            assertEquals(1, count("""
                    SELECT count(DISTINCT a.id) FROM assessment a
                    JOIN event ev ON ev.id=a.event_id
                    JOIN event_entity ee ON ee.event_id=ev.id
                    JOIN assessment_evidence ae ON ae.assessment_id=a.id
                    WHERE ee.entity_id=? AND a.analysis_version='official-annual-filing-v1'
                      AND a.status='COMPLETED'
                    """, companyId));
            var experience = companyEvents.find(companyId);
            assertEquals(1, experience.events().size());
            assertEquals("OpenDART", experience.events().getFirst().evidence().sourceName());
        }

        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,created_at,updated_at)
                VALUES(?,'DemoVerifier','demoverifier','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, userId);
        for (UUID companyId : companyIds(first)) {
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,interest_level,alert_enabled,created_at,updated_at)
                    SELECT gen_random_uuid(),?,?, 'HIGH',true,
                           min(a.completed_at) - interval '1 second',
                           min(a.completed_at) - interval '1 second'
                    FROM assessment a
                    JOIN event ev ON ev.id=a.event_id
                    JOIN event_entity ee ON ee.event_id=ev.id
                    WHERE ee.entity_id=? AND a.status='COMPLETED'
                    """, userId, companyId, companyId);
        }

        var briefing = briefings.getOrCreate(userId);
        assertEquals("READY", briefing.status());
        assertEquals(2, briefing.items().size());
        assertEquals(companyIds(first), briefing.items().stream()
                .flatMap(item -> item.companies().stream())
                .map(com.aira.api.delivery.dto.RelatedCompany::companyId)
                .collect(Collectors.toUnmodifiableSet()));

        var alertResult = alerts.reconcile(userId);
        assertEquals(2, alertResult.alerts().size());
        assertEquals(companyIds(first), alertResult.alerts().stream()
                .flatMap(item -> item.companies().stream())
                .map(com.aira.api.delivery.dto.RelatedCompany::companyId)
                .collect(Collectors.toUnmodifiableSet()));
        assertTrue(alertResult.alerts().stream()
                .allMatch(alert -> "OpenDART".equals(alert.sourceName())));
    }

    private int count(String sql, UUID companyId) {
        return jdbc.queryForObject(sql, Integer.class, companyId);
    }

    private static Set<UUID> companyIds(OfficialDemoBootstrapOperation.Result result) {
        return result.companies().stream()
                .map(OfficialDemoBootstrapOperation.PreparedCompany::entityId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
