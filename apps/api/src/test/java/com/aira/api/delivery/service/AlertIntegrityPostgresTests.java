package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.user.service.UserInterestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AlertIntegrityPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InAppAlertService alerts;
    @Autowired UserInterestService interests;
    @Autowired com.aira.api.analysis.query.CurrentAssessmentQuery currentAssessments;
    @Autowired com.aira.api.analysis.query.HistoricalAssessmentQuery historicalAssessments;

    @Test void existingSecurityInterestRetainsExactPrivateDelivery() {
        Fixture f = createFixture();
        try {
            jdbc.update("UPDATE entity SET entity_type='SECURITY',market_code='KOSPI',symbol='000001' WHERE id=?", f.entity());
            OffsetDateTime enabled = interests.setAlertEnabled(f.user(), f.entity(), true).alertEnabledAt();
            insertAssessment(f.a1(), f.event(), f.a0(), "security-a1", enabled.plusSeconds(1));
            linkEvidence(f.a1(), f.evidence1());
            var delivered = alerts.reconcile(f.user()).alerts();
            assertEquals(1, delivered.size());
            assertEquals(f.a1(), delivered.getFirst().assessmentId());
            interests.remove(f.user(), f.entity());
            assertEquals(f.a1(), alerts.findOwned(f.user(), delivered.getFirst().alertId()).assessmentId());
        } finally { cleanup(f); }
    }

    @Test void oneSharedAssessmentSupportsTwoPrivateUsersAndOneDeletionDoesNotRetargetTruth() {
        Fixture f = createFixture();
        try {
            OffsetDateTime firstEnabled = interests.setAlertEnabled(f.user(), f.entity(), true).alertEnabledAt();
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,alert_enabled,alert_enabled_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,true,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, f.otherUser(), f.entity(), firstEnabled);
            insertAssessment(f.a1(), f.event(), f.a0(), "shared-a1", firstEnabled.plusSeconds(1));
            linkEvidence(f.a1(), f.evidence1());
            var first = alerts.reconcile(f.user()).alerts();
            var second = alerts.reconcile(f.otherUser()).alerts();
            assertEquals(1, first.size());
            assertEquals(1, second.size());
            assertEquals(f.a1(), first.getFirst().assessmentId());
            assertEquals(f.a1(), second.getFirst().assessmentId());
            assertThrows(AlertNotFoundException.class,
                    () -> alerts.findOwned(f.otherUser(), first.getFirst().alertId()));
            interests.remove(f.user(), f.entity());
            assertEquals(1, alerts.reconcile(f.otherUser()).alerts().size());
            assertEquals(1, count("SELECT count(*) FROM assessment WHERE id=?", f.a1()));
            assertEquals(2, count("SELECT count(*) FROM alert WHERE assessment_id=?", f.a1()));
            insertAssessment(f.a2(), f.event(), f.a1(), "shared-a2", firstEnabled.plusSeconds(2));
            linkEvidence(f.a2(), f.evidence2());
            assertEquals(f.a2(), currentAssessments.find(f.event()).assessmentId());
            assertEquals(f.a1(), historicalAssessments.find(f.a1()).assessmentId());
            assertEquals(2, alerts.reconcile(f.otherUser()).alerts().size());
            assertEquals(1, count("SELECT count(*) FROM alert WHERE user_id=? AND assessment_id=?",
                    f.otherUser(), f.a2()));
        } finally { cleanup(f); }
    }

    @Test void onlyLiveOriginCanCreateAlertAndRetryReusesIt() {
        Fixture f = createFixture();
        try {
            OffsetDateTime enabledAt = interests.setAlertEnabled(f.user(), f.entity(), true).alertEnabledAt();
            insertAssessment(f.a1(), f.event(), f.a0(), "origin-v1", enabledAt.plusSeconds(1));
            linkEvidence(f.a1(), f.evidence1());
            jdbc.update("UPDATE event SET ingestion_origin='BACKFILL' WHERE id=?", f.event());
            assertTrue(alerts.reconcile(f.user()).alerts().isEmpty());
            jdbc.update("UPDATE event SET ingestion_origin='LEGACY_UNKNOWN' WHERE id=?", f.event());
            assertTrue(alerts.reconcile(f.user()).alerts().isEmpty());
            jdbc.update("UPDATE event SET ingestion_origin='LIVE' WHERE id=?", f.event());
            assertEquals(1, alerts.reconcile(f.user()).alerts().size());
            assertEquals(1, alerts.reconcile(f.user()).alerts().size());
            assertEquals(1, count("SELECT count(*) FROM alert WHERE user_id=?", f.user()));
        } finally { cleanup(f); }
    }

    @Test
    void activationTerminalSuccessorsDedupConcurrencyHistoricalAndConstraints() throws Exception {
        Fixture f = createFixture();
        try {
            var beforeOptIn = alerts.reconcile(f.user());
            assertEquals("NO_ALERT_ENABLED_INTERESTS", beforeOptIn.emptyReason());
            assertTrue(beforeOptIn.alerts().isEmpty());

            var enabled = interests.setAlertEnabled(f.user(), f.entity(), true);
            OffsetDateTime activation = enabled.alertEnabledAt();
            assertNotNull(activation);
            OffsetDateTime persistedActivation = timestamp(
                    "SELECT alert_enabled_at FROM user_interest WHERE user_id=? AND entity_id=?",
                    f.user(), f.entity());
            OffsetDateTime firstUpdated = timestamp(
                    "SELECT updated_at FROM user_interest WHERE user_id=? AND entity_id=?",
                    f.user(), f.entity());
            var retry = interests.setAlertEnabled(f.user(), f.entity(), true);
            assertEquals(persistedActivation, retry.alertEnabledAt());
            assertEquals(firstUpdated, timestamp(
                    "SELECT updated_at FROM user_interest WHERE user_id=? AND entity_id=?",
                    f.user(), f.entity()));

            insertAssessment(f.a1(), f.event(), f.a0(), "alert-v1",
                    persistedActivation.plusSeconds(20));
            linkEvidence(f.a1(), f.evidence1());
            linkEvidence(f.a1(), f.evidence2());

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> alerts.reconcile(f.user()));
                var second = executor.submit(() -> alerts.reconcile(f.user()));
                assertEquals(List.of(f.a1()), first.get().alerts().stream()
                        .map(item -> item.assessmentId()).toList());
                assertEquals(List.of(f.a1()), second.get().alerts().stream()
                        .map(item -> item.assessmentId()).toList());
            }
            assertEquals(1, count("""
                    SELECT count(*) FROM alert
                    WHERE user_id=? AND assessment_id=? AND policy_version=?
                    """, f.user(), f.a1(), InAppAlertService.POLICY));

            insertAssessment(f.a2(), f.event(), f.a1(), "alert-v2",
                    persistedActivation.plusSeconds(10));
            linkEvidence(f.a2(), f.evidence1());
            var afterA2 = alerts.reconcile(f.user());
            assertEquals(2, afterA2.alerts().size());
            assertTrue(afterA2.alerts().stream()
                    .anyMatch(item -> item.assessmentId().equals(f.a2())));

            insertAssessment(f.a3(), f.event(), f.a2(), "alert-v3",
                    persistedActivation.plusSeconds(30));
            linkEvidence(f.a3(), f.evidence2());
            var afterA3 = alerts.reconcile(f.user());
            assertEquals(3, afterA3.alerts().size());
            assertTrue(afterA3.alerts().stream()
                    .anyMatch(item -> item.assessmentId().equals(f.a3())));
            assertEquals(3, count("""
                    SELECT count(*) FROM alert al JOIN assessment a ON a.id=al.assessment_id
                    WHERE al.user_id=? AND a.event_id=?
                    """, f.user(), f.event()));
            assertEquals(3, afterA3.alerts().stream().map(item -> item.assessmentId())
                    .distinct().count());

            UUID a1AlertId = afterA3.alerts().stream()
                    .filter(item -> item.assessmentId().equals(f.a1()))
                    .findFirst().orElseThrow().alertId();
            var historicalA1 = alerts.findOwned(f.user(), a1AlertId);
            assertEquals(f.a1(), historicalA1.assessmentId());
            assertEquals(f.event(), historicalA1.eventId());
            assertEquals("alert-v1", historicalA1.analysisVersion());
            assertEquals("RULE", historicalA1.method());
            assertEquals("MEDIUM", historicalA1.importance());
            assertEquals("HIGH", historicalA1.confidence());
            assertEquals("Known uncertainty", historicalA1.uncertainty());
            assertEquals(persistedActivation.plusSeconds(20).toInstant(),
                    historicalA1.completedAt().toInstant());
            assertEquals(List.of(f.evidence1(), f.evidence2()).stream().sorted().toList(),
                    historicalA1.evidence().stream().map(reference -> reference.evidenceId())
                            .sorted().toList());
            assertTrue(historicalA1.evidence().stream()
                    .allMatch(reference -> reference.revision() > 0
                            && reference.sourceName().equals("Alert test")));

            interests.setAlertEnabled(f.user(), f.entity(), false);
            assertNull(timestamp("SELECT alert_enabled_at FROM user_interest WHERE user_id=?",
                    f.user()));
            UUID disabledEvent = f.disabledEvent();
            OffsetDateTime disabledAssessmentTime = OffsetDateTime.now(ZoneOffset.UTC);
            insertAssessment(f.b0(), disabledEvent, null, "disabled-v1",
                    disabledAssessmentTime);
            linkEvidence(f.b0(), f.evidence1());
            assertEquals(3, alerts.reconcile(f.user()).alerts().size());
            Thread.sleep(20);
            OffsetDateTime reenabledAt = interests.setAlertEnabled(
                    f.user(), f.entity(), true).alertEnabledAt();
            assertTrue(reenabledAt.isAfter(disabledAssessmentTime));
            assertEquals(3, alerts.reconcile(f.user()).alerts().size());
            insertAssessment(f.b1(), disabledEvent, f.b0(), "reenabled-v2",
                    reenabledAt.plusSeconds(1));
            linkEvidence(f.b1(), f.evidence1());
            assertEquals(4, alerts.reconcile(f.user()).alerts().size());
            assertEquals(1, count("""
                    SELECT count(*) FROM alert WHERE user_id=? AND assessment_id=?
                    """, f.user(), f.b1()));
            assertEquals(0, count("""
                    SELECT count(*) FROM alert WHERE user_id=? AND assessment_id=?
                    """, f.user(), f.b0()));

            assertEquals(0, count("""
                    SELECT count(*) FROM alert
                    WHERE octet_length(dedup_key)<>32
                       OR (status='SENT' AND sent_at IS NULL)
                    """));
            byte[] actualDedup = jdbc.queryForObject("""
                    SELECT dedup_key FROM alert
                    WHERE user_id=? AND assessment_id=? AND policy_version=?
                    """, byte[].class, f.user(), f.a1(), InAppAlertService.POLICY);
            assertArrayEquals(InAppAlertService.digest(
                    f.user(), f.a1(), InAppAlertService.POLICY), actualDedup);

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,
                                      status,sent_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,?,'DUPLICATE',?,
                           'SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, f.user(), f.a1(), InAppAlertService.POLICY, new byte[32]));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,
                                      status,sent_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,'other-policy','BAD_DEDUP',decode('01','hex'),
                           'SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, f.user(), f.a1()));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,
                                      status,sent_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,'null-sent-policy','NULL_SENT',
                           ?,'SENT',NULL,
                           CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, f.user(), f.a1(), sha256Variant()));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    UPDATE user_interest SET alert_enabled_at=NULL
                    WHERE user_id=? AND alert_enabled=true
                    """, f.user()));

            assertThrows(AlertNotFoundException.class,
                    () -> alerts.findOwned(f.otherUser(), a1AlertId));
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", f.user());
            var historicalAfterInterestDelete = alerts.findOwned(f.user(), a1AlertId);
            assertEquals(f.a1(), historicalAfterInterestDelete.assessmentId());
            assertEquals(List.of(f.evidence1(), f.evidence2()).stream().sorted().toList(),
                    historicalAfterInterestDelete.evidence().stream()
                            .map(reference -> reference.evidenceId()).sorted().toList());
        } finally {
            cleanup(f);
        }
    }

    @Test
    void emptyReasonsDistinguishInterestConsentEligibilityAndSentHistory() {
        UUID user = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        try {
            createUser(user, "AlertEmpty");
            createEntity(entity, "Empty");
            assertEquals("NO_INTERESTS", alerts.reconcile(user).emptyReason());
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,alert_enabled,
                                              alert_enabled_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,false,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, user, entity);
            assertEquals("NO_ALERT_ENABLED_INTERESTS", alerts.reconcile(user).emptyReason());
            jdbc.update("""
                    UPDATE user_interest
                    SET alert_enabled=true,alert_enabled_at=CURRENT_TIMESTAMP,
                        updated_at=CURRENT_TIMESTAMP
                    WHERE user_id=? AND entity_id=?
                    """, user, entity);
            assertEquals("NO_ELIGIBLE_ASSESSMENTS", alerts.reconcile(user).emptyReason());
            assertEquals("NO_SENT_ALERTS", alerts.findAll(user).emptyReason());
        } finally {
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", user);
            jdbc.update("DELETE FROM entity WHERE id=?", entity);
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
    }

    private Fixture createFixture() {
        Fixture f = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        try {
            createUser(f.user(), "AlertA");
            createUser(f.otherUser(), "AlertB");
            createEntity(f.entity(), "Primary");
            jdbc.update("""
                    INSERT INTO source(id,source_type,name)
                    VALUES(?,'REGULATOR','Alert test')
                    """, f.source());
            insertEvidence(f.evidence1(), f.source(), "ALERT-E-1", 1);
            insertEvidence(f.evidence2(), f.source(), "ALERT-E-2", 2);
            createEvent(f.event(), f.entity(), "Alert primary");
            createEvent(f.disabledEvent(), f.entity(), "Alert disabled interval");
            jdbc.update("""
                    INSERT INTO event_evidence(event_id,evidence_id,relation_type)
                    VALUES(?,?,'SUPPORTS'),(?,?,'SUPPORTS')
                    """, f.event(), f.evidence1(), f.disabledEvent(), f.evidence1());
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,alert_enabled,
                                              alert_enabled_at,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,false,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, f.user(), f.entity());
            insertAssessment(f.a0(), f.event(), null, "pre-opt-in",
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
            linkEvidence(f.a0(), f.evidence1());
            return f;
        } catch (RuntimeException failure) {
            cleanup(f);
            throw failure;
        }
    }

    private void createUser(UUID id, String prefix) {
        String nickname = prefix + id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,created_at,updated_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, nickname, nickname.toLowerCase(Locale.ROOT));
    }

    private void createEntity(UUID id, String suffix) {
        jdbc.update("""
                INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active,
                                   created_at,updated_at)
                VALUES(?,'COMPANY',?,?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, "Alert " + suffix, "COMPANY:" + id);
    }

    private void createEvent(UUID event, UUID entity, String title) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO event(id,event_type,title,occurred_at,first_observed_at,last_observed_at,
                                  status,created_at,updated_at,ingestion_origin)
                VALUES(?,'EARNINGS',?,?,?,?, 'CONFIRMED',?,?,'LIVE')
                """, event, title, now, now, now, now, now);
        jdbc.update("""
                INSERT INTO event_entity(event_id,entity_id,relation_type,relevance)
                VALUES(?,?,'SUBJECT','HIGH')
                """, event, entity);
    }

    private void insertEvidence(UUID id, UUID source, String externalId, int revision) {
        jdbc.update("""
                INSERT INTO evidence(id,source_id,evidence_type,external_id,original_url,title,
                                     content_hash,published_at,collected_at,revision,status)
                VALUES(?,?,'DISCLOSURE',?,?,?,decode(replace(?::text,'-',''),'hex'),
                       CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'ACTIVE')
                """, id, source, externalId, "https://alert.test/" + externalId,
                "Evidence " + externalId, UUID.randomUUID(), revision);
    }

    private void insertAssessment(UUID id, UUID event, UUID predecessor, String version,
            OffsetDateTime completedAt) {
        jdbc.update("""
                INSERT INTO assessment(id,event_id,analysis_version,method,importance,summary,
                                       confidence,uncertainty,time_horizon,status,input_fingerprint,
                                       completed_at,supersedes_assessment_id,created_at,updated_at)
                VALUES(?, ?,?,'RULE','MEDIUM',?,'HIGH','Known uncertainty','SHORT_TERM',
                       'COMPLETED',decode(replace(?::text,'-',''),'hex'),?,?,?,?)
                """, id, event, version, "Summary " + version, UUID.randomUUID(), completedAt,
                predecessor, completedAt, completedAt);
    }

    private void linkEvidence(UUID assessment, UUID evidence) {
        jdbc.update("""
                INSERT INTO assessment_evidence(assessment_id,evidence_id,usage_type)
                VALUES(?,?,'SUPPORTS')
                """, assessment, evidence);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private OffsetDateTime timestamp(String sql, Object... args) {
        return jdbc.queryForObject(sql, OffsetDateTime.class, args);
    }

    private static byte[] sha256Variant() {
        byte[] bytes = new byte[32];
        bytes[31] = 1;
        return bytes;
    }

    private void cleanup(Fixture f) {
        if (f == null) return;
        jdbc.update("DELETE FROM alert WHERE user_id IN (?,?)", f.user(), f.otherUser());
        jdbc.update("DELETE FROM user_interest WHERE user_id IN (?,?)", f.user(), f.otherUser());
        jdbc.update("DELETE FROM assessment_evidence WHERE assessment_id IN (?,?,?,?,?,?)",
                f.a0(), f.a1(), f.a2(), f.a3(), f.b0(), f.b1());
        jdbc.update("DELETE FROM assessment WHERE id IN (?,?,?,?,?,?)",
                f.b1(), f.b0(), f.a3(), f.a2(), f.a1(), f.a0());
        jdbc.update("DELETE FROM event_evidence WHERE event_id IN (?,?)",
                f.event(), f.disabledEvent());
        jdbc.update("DELETE FROM event_entity WHERE event_id IN (?,?)",
                f.event(), f.disabledEvent());
        jdbc.update("DELETE FROM event WHERE id IN (?,?)", f.event(), f.disabledEvent());
        jdbc.update("DELETE FROM evidence WHERE id IN (?,?)", f.evidence1(), f.evidence2());
        jdbc.update("DELETE FROM entity WHERE id=?", f.entity());
        jdbc.update("DELETE FROM source WHERE id=?", f.source());
        jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", f.user(), f.otherUser());
    }

    private record Fixture(UUID source, UUID entity, UUID user, UUID otherUser, UUID event,
            UUID disabledEvent, UUID evidence1, UUID evidence2, UUID a0, UUID a1, UUID a2,
            UUID a3, UUID b0) {
        UUID b1() {
            return UUID.nameUUIDFromBytes((b0 + ":successor").getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
