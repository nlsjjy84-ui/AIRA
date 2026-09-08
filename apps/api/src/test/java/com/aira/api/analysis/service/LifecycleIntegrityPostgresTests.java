package com.aira.api.analysis.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.analysis.controller.HistoricalAssessmentController;
import com.aira.api.analysis.domain.Assessment;
import com.aira.api.analysis.query.HistoricalAssessmentQuery;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.EventStatus;
import com.aira.api.market.normalization.EarningsEventNormalizationService;
import com.aira.api.market.normalization.EarningsNormalizationInput;
import com.aira.api.market.repository.EventRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class LifecycleIntegrityPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EarningsEventNormalizationService normalization;
    @Autowired RuleBasedEarningsAssessmentService assessmentService;
    @Autowired EventRepository events;
    @Autowired HistoricalAssessmentQuery historical;
    @Autowired EntityManager entityManager;

    @Test
    void eventAndAssessmentLifecycleRunAgainstPostgres() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Fixture f = fixture(3);
            Event candidate = candidate(f, 0);
            assertThrows(IllegalStateException.class,
                    () -> assessmentService.assess(candidate.getId(), f.evidence(0)));
            assertThrows(IllegalStateException.class,
                    () -> candidate.confirm(false, true, OffsetDateTime.now()));
            assertThrows(IllegalStateException.class,
                    () -> candidate.confirm(true, false, OffsetDateTime.now()));

            Event confirmed = normalize(f, 0);
            assertEquals(EventStatus.CONFIRMED, confirmed.getStatus());
            assertEquals(confirmed.getId(), normalize(f, 0).getId());
            assertThrows(IllegalArgumentException.class,
                    () -> assessmentService.assess(confirmed.getId(), f.evidence(1)));

            linkEvidence(confirmed.getId(), f.evidence(1));
            Assessment completed = assessmentService.assess(confirmed.getId(), f.evidence(1));
            assertEquals("COMPLETED", completed.getStatus().name());
            assertEquals(1, count("SELECT count(*) FROM assessment_evidence WHERE assessment_id=?",
                    completed.getId()));
            assertEquals(completed.getId(),
                    assessmentService.assess(confirmed.getId(), f.evidence(1)).getId());

            jdbc.update("UPDATE event SET status='DISCARDED' WHERE id=?", candidate.getId());
            entityManager.clear();
            Event invalid = events.findById(candidate.getId()).orElseThrow();
            assertThrows(IllegalStateException.class,
                    () -> invalid.confirm(true, true, OffsetDateTime.now()));
            status.setRollbackOnly();
        });
    }

    @Test
    void supersessionGraphAndHistoricalExactUseIdentityNotTimestamp() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Fixture f = fixture(4);
            Event event = normalize(f, 0);
            linkEvidence(event.getId(), f.evidence(1));
            linkEvidence(event.getId(), f.evidence(2));
            Assessment a1 = assessmentService.assess(event.getId(), f.evidence(0));
            Assessment a2 = assessmentService.assess(event.getId(), f.evidence(1));
            Assessment a3 = assessmentService.assess(event.getId(), f.evidence(2));
            assertNull(a1.getSupersedesAssessment());
            assertEquals(a1.getId(), a2.getSupersedesAssessment().getId());
            assertEquals(a2.getId(), a3.getSupersedesAssessment().getId());
            jdbc.update("UPDATE assessment SET completed_at=CURRENT_TIMESTAMP + interval '2 day' WHERE id=?", a1.getId());
            jdbc.update("UPDATE assessment SET completed_at=CURRENT_TIMESTAMP - interval '2 day' WHERE id=?", a3.getId());
            assertEquals(a3.getId(), terminal(event.getId()));

            var exact = historical.find(a1.getId());
            assertEquals(a1.getId(), exact.assessmentId());
            assertEquals(event.getId(), exact.eventId());
            assertNull(exact.supersedesAssessmentId());
            assertEquals(java.util.List.of(f.evidence(0)), exact.evidenceIds());
            assertEquals(RuleBasedEarningsAssessmentService.VERSION, exact.analysisVersion());
            assertEquals("RULE", exact.method());
            assertEquals("MEDIUM", exact.confidence());
            assertNotNull(exact.uncertainty());
            assertNotNull(exact.completedAt());
            try {
                MockMvcBuilders.standaloneSetup(new HistoricalAssessmentController(historical))
                        .build().perform(get("/api/assessments/{id}", a1.getId()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.assessmentId").value(a1.getId().toString()))
                        .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                        .andExpect(jsonPath("$.supersedesAssessmentId").doesNotExist())
                        .andExpect(jsonPath("$.evidenceIds[0]").value(f.evidence(0).toString()))
                        .andExpect(jsonPath("$.analysisVersion").value(RuleBasedEarningsAssessmentService.VERSION))
                        .andExpect(jsonPath("$.method").value("RULE"))
                        .andExpect(jsonPath("$.confidence").value("MEDIUM"))
                        .andExpect(jsonPath("$.uncertainty").isNotEmpty())
                        .andExpect(jsonPath("$.completedAt").isNotEmpty());
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            Event other = normalize(f, 3);
            assertThrows(IllegalArgumentException.class, () ->
                    com.aira.api.analysis.domain.Assessment.completedRule(other, "cross-event",
                            com.aira.api.analysis.domain.Importance.MEDIUM, "summary",
                            com.aira.api.analysis.domain.Confidence.MEDIUM, "uncertainty",
                            com.aira.api.analysis.domain.TimeHorizon.UNSPECIFIED,
                            new byte[] {99}, a3, OffsetDateTime.now()));
            jdbc.update("UPDATE assessment SET supersedes_assessment_id=? WHERE id=?", a3.getId(), a1.getId());
            linkEvidence(event.getId(), f.evidence(3));
            entityManager.clear();
            assertThrows(IllegalStateException.class,
                    () -> assessmentService.assess(event.getId(), f.evidence(3)));
            status.setRollbackOnly();
        });
    }

    @Test
    void assessmentEvidenceFailureRollsBackTheCompletedAssessment() {
        Fixture f = new TransactionTemplate(transactionManager).execute(status -> fixture(1));
        UUID eventId = new TransactionTemplate(transactionManager).execute(
                status -> normalize(f, 0).getId());
        try {
            int before = count("SELECT count(*) FROM assessment WHERE event_id=?", eventId);
            assertThrows(RuntimeException.class, () ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbc.execute("""
                                ALTER TABLE assessment_evidence
                                ADD CONSTRAINT ck_lifecycle_closure_reject_supports
                                CHECK (usage_type <> 'SUPPORTS') NOT VALID
                                """);
                        assessmentService.assess(eventId, f.evidence(0));
                    }));
            assertEquals(before, count("SELECT count(*) FROM assessment WHERE event_id=?", eventId));
            assertEquals(0, count("""
                    SELECT count(*) FROM pg_constraint
                    WHERE conname='ck_lifecycle_closure_reject_supports'
                    """));
        } finally {
            cleanup(f);
        }
    }

    @Test
    void concurrentAssessmentCreationSerializesOnTheEventLock() throws Exception {
        Fixture f = new TransactionTemplate(transactionManager).execute(status -> fixture(3));
        UUID eventId = new TransactionTemplate(transactionManager).execute(status -> {
            Event event = normalize(f, 0);
            linkEvidence(event.getId(), f.evidence(1));
            return event.getId();
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var task1 = executor.submit(() -> { ready.countDown(); start.await(); return assessmentService.assess(eventId, f.evidence(0)).getId(); });
            var task2 = executor.submit(() -> { ready.countDown(); start.await(); return assessmentService.assess(eventId, f.evidence(1)).getId(); });
            ready.await();
            start.countDown();
            UUID first = task1.get();
            UUID second = task2.get();
            assertNotEquals(first, second);
            assertEquals(1, count("""
                    SELECT count(*) FROM assessment a WHERE a.event_id=? AND a.status='COMPLETED'
                      AND NOT EXISTS (SELECT 1 FROM assessment child
                                      WHERE child.supersedes_assessment_id=a.id)
                    """, eventId));
            assertEquals(first, assessmentService.assess(eventId, f.evidence(0)).getId());
        } finally {
            cleanup(f);
        }
    }

    private Fixture fixture(int evidenceCount) {
        UUID source = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        jdbc.update("INSERT INTO source(id,source_type,name,external_key) VALUES(?,'REGULATOR','Lifecycle closure',?)",
                source, "closure-" + source);
        jdbc.update("INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active,created_at,updated_at) VALUES(?,'COMPANY','Lifecycle company',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                entity, "COMPANY:" + entity);
        UUID[] evidence = new UUID[evidenceCount];
        for (int i=0;i<evidenceCount;i++) {
            evidence[i]=UUID.randomUUID();
            jdbc.update("INSERT INTO evidence(id,source_id,evidence_type,original_url,content_hash,collected_at,revision,status) VALUES(?,?,'DISCLOSURE',?,?,CURRENT_TIMESTAMP,1,'ACTIVE')",
                    evidence[i], source, "https://closure.test/" + evidence[i], new byte[] {(byte)(i+1)});
        }
        return new Fixture(source, entity, evidence);
    }

    private Event candidate(Fixture f, int index) {
        return events.saveAndFlush(Event.createEarnings("candidate", OffsetDateTime.now(),
                OffsetDateTime.now(), UUID.randomUUID().toString().getBytes(), OffsetDateTime.now()));
    }

    private Event normalize(Fixture f, int index) {
        return normalization.normalize(new EarningsNormalizationInput(f.entity(), f.evidence(index),
                LocalDate.of(2025, 12, 31).minusYears(index), "closure earnings " + index,
                OffsetDateTime.now().minusYears(index)));
    }

    private void linkEvidence(UUID eventId, UUID evidenceId) {
        jdbc.update("INSERT INTO event_evidence(event_id,evidence_id,relation_type) VALUES(?,?,'SUPPORTS') ON CONFLICT DO NOTHING",
                eventId, evidenceId);
    }

    private UUID terminal(UUID eventId) {
        return jdbc.queryForObject("""
                SELECT a.id FROM assessment a WHERE a.event_id=? AND a.status='COMPLETED'
                  AND NOT EXISTS (SELECT 1 FROM assessment child WHERE child.supersedes_assessment_id=a.id)
                """, UUID.class, eventId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM assessment_evidence WHERE assessment_id IN (SELECT id FROM assessment WHERE event_id IN (SELECT event_id FROM event_entity WHERE entity_id=?))", f.entity());
        jdbc.update("DELETE FROM assessment WHERE event_id IN (SELECT event_id FROM event_entity WHERE entity_id=?)", f.entity());
        jdbc.update("DELETE FROM event_evidence WHERE event_id IN (SELECT event_id FROM event_entity WHERE entity_id=?)", f.entity());
        jdbc.update("DELETE FROM event_entity WHERE entity_id=?", f.entity());
        jdbc.update("DELETE FROM event WHERE id NOT IN (SELECT event_id FROM event_entity) AND title LIKE 'closure earnings %'");
        jdbc.update("DELETE FROM evidence WHERE source_id=?", f.source());
        jdbc.update("DELETE FROM entity WHERE id=?", f.entity());
        jdbc.update("DELETE FROM source WHERE id=?", f.source());
    }

    private record Fixture(UUID source, UUID entity, UUID[] evidence) {
        UUID evidence(int index) { return evidence[index]; }
    }
}
