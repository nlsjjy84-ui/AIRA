package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
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
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class BriefingIntegrityPostgresTests {
    private static final Instant DAY_2 = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant DAY_10 = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant DAY_15 = Instant.parse("2026-08-15T00:00:00Z");

    @Autowired JdbcTemplate jdbc;

    @Test void exactSecurityInterestCanRetainBriefingWithoutCompanyPropagation() {
        Fixture f = createFixture();
        try {
            jdbc.update("DELETE FROM user_interest WHERE user_id=? AND entity_id=?", f.user(), f.entity2());
            jdbc.update("UPDATE entity SET entity_type='SECURITY',market_code='KOSPI',symbol='000001' WHERE id=?", f.entity1());
            var briefing = serviceAt(DAY_2).getOrCreate(f.user());
            assertEquals(f.a1(), briefing.items().getFirst().assessmentId());
            assertEquals(1, count("SELECT count(*) FROM user_interest WHERE user_id=?", f.user()));
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", f.user());
            assertEquals(f.a1(), serviceAt(DAY_2).findOwned(f.user(), briefing.briefingId())
                    .items().getFirst().assessmentId());
        } finally { cleanup(f); }
    }

    @Test void sharedAssessmentProducesPrivateBriefingsForTwoUsers() {
        Fixture f = createFixture();
        try {
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,?,?)
                    """, f.otherUser(), f.entity1(), utc(DAY_2.minusSeconds(1_000)),
                    utc(DAY_2.minusSeconds(1_000)));
            var service = serviceAt(DAY_2);
            var first = service.getOrCreate(f.user());
            var second = service.getOrCreate(f.otherUser());
            assertEquals(f.a1(), first.items().getFirst().assessmentId());
            assertEquals(f.a1(), second.items().getFirst().assessmentId());
            assertTrue(!first.briefingId().equals(second.briefingId()));
            assertThrows(BriefingNotFoundException.class,
                    () -> service.findOwned(f.otherUser(), first.briefingId()));
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", f.user());
            assertEquals(second.briefingId(), service.getOrCreate(f.otherUser()).briefingId());
            assertEquals(1, count("SELECT count(*) FROM assessment WHERE id=?", f.a1()));
        } finally { cleanup(f); }
    }

    @Test void onlyLiveOriginCanCreateBriefingAndRetryReusesIt() {
        Fixture f = createFixture();
        try {
            jdbc.update("UPDATE event SET ingestion_origin='BACKFILL' WHERE id=?", f.event());
            assertEquals("NO_ELIGIBLE_ASSESSMENTS", serviceAt(DAY_2).getOrCreate(f.user()).emptyReason());
            jdbc.update("UPDATE event SET ingestion_origin='LEGACY_UNKNOWN' WHERE id=?", f.event());
            assertEquals("NO_ELIGIBLE_ASSESSMENTS", serviceAt(DAY_2).getOrCreate(f.user()).emptyReason());
            jdbc.update("UPDATE event SET ingestion_origin='LIVE' WHERE id=?", f.event());
            var first = serviceAt(DAY_2).getOrCreate(f.user());
            assertEquals(f.a1(), first.items().getFirst().assessmentId());
            assertEquals(first.briefingId(), serviceAt(DAY_2.plusSeconds(1)).getOrCreate(f.user()).briefingId());
            assertEquals(1, count("SELECT count(*) FROM briefing WHERE user_id=?", f.user()));
        } finally { cleanup(f); }
    }

    @Test
    void cutoffTerminalMultiInterestEvidenceHistoricalOwnershipAndConcurrency() throws Exception {
        Fixture f = createFixture();
        try {
            PersonalBriefingService atDay2 = serviceAt(DAY_2);
            var original = atDay2.getOrCreate(f.user());
            assertEquals(f.a1(), original.items().getFirst().assessmentId());
            for (int retry = 0; retry < 5; retry++) {
                var repeated = serviceAt(DAY_2.plusSeconds(retry + 1)).getOrCreate(f.user());
                assertEquals(original.briefingId(), repeated.briefingId());
                assertEquals(List.of(f.a1()), repeated.items().stream()
                        .map(item -> item.assessmentId()).toList());
            }
            assertEquals(1, count("SELECT count(*) FROM briefing WHERE user_id=?", f.user()));

            insertAssessment(f.a2(), f.event(), f.a1(), "rule-v2", DAY_10.minusSeconds(100));
            linkEvidence(f.a2(), f.evidence1());
            linkEvidence(f.a2(), f.evidence2());
            PersonalBriefingService atDay10 = serviceAt(DAY_10);
            List<com.aira.api.delivery.dto.BriefingResponse> concurrent;
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> atDay10.getOrCreate(f.user()));
                var second = executor.submit(() -> atDay10.getOrCreate(f.user()));
                concurrent = List.of(first.get(), second.get());
            }
            assertEquals(concurrent.getFirst().briefingId(), concurrent.getLast().briefingId());
            var day10 = concurrent.getFirst();
            assertEquals("READY", day10.status());
            assertEquals(1, day10.items().size());
            assertEquals(f.a2(), day10.items().getFirst().assessmentId());
            assertEquals("rule-v2", day10.items().getFirst().analysisVersion());
            assertEquals(2, day10.items().getFirst().evidence().size());
            assertEquals(List.of(f.evidence1(), f.evidence2()).stream().sorted().toList(),
                    day10.items().getFirst().evidence().stream()
                            .map(reference -> reference.evidenceId()).sorted().toList());
            assertEquals(1, count("SELECT count(*) FROM briefing_item WHERE briefing_id=?",
                    day10.briefingId()));
            assertEquals(day10.briefingId(), serviceAt(DAY_10.plusSeconds(1))
                    .getOrCreate(f.user()).briefingId());

            insertAssessment(f.a3(), f.event(), f.a2(), "rule-v3", DAY_15.minusSeconds(1));
            linkEvidence(f.a3(), f.evidence1());
            var day15 = serviceAt(DAY_15).getOrCreate(f.user());
            assertEquals(f.a3(), day15.items().getFirst().assessmentId());
            assertEquals(day15.briefingId(), serviceAt(DAY_15.plusSeconds(1))
                    .getOrCreate(f.user()).briefingId());

            jdbc.update("DELETE FROM user_interest WHERE user_id=?", f.user());
            var historicalOriginal = atDay2.findOwned(f.user(), original.briefingId());
            assertEquals(f.a1(), historicalOriginal.items().getFirst().assessmentId());
            assertEquals(f.event(), historicalOriginal.items().getFirst().eventId());
            assertEquals(1, historicalOriginal.items().getFirst().evidence().size());
            var historicalDay10 = atDay10.findOwned(f.user(), day10.briefingId());
            assertEquals(f.a2(), historicalDay10.items().getFirst().assessmentId());
            assertEquals(2, historicalDay10.items().getFirst().evidence().size());
            assertThrows(BriefingNotFoundException.class,
                    () -> atDay10.findOwned(f.otherUser(), day10.briefingId()));
        } finally {
            cleanup(f);
        }
    }

    @Test
    void emptyReasonsAndExclusiveInclusivePeriodBoundariesAreExplicit() {
        UUID user = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        try {
            createUser(user, "BriefEmpty");
            createEntity(entity, "Empty");
            assertEquals("NO_INTERESTS", serviceAt(DAY_10).getOrCreate(user).emptyReason());
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,?,?)
                    """, user, entity, utc(DAY_10.minusSeconds(1)), utc(DAY_10.minusSeconds(1)));
            assertEquals("NO_ELIGIBLE_ASSESSMENTS",
                    serviceAt(DAY_10).getOrCreate(user).emptyReason());
        } finally {
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", user);
            jdbc.update("DELETE FROM entity WHERE id=?", entity);
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
    }

    private PersonalBriefingService serviceAt(Instant instant) {
        return new PersonalBriefingService(jdbc, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private Fixture createFixture() {
        UUID source=UUID.randomUUID(), entity1=UUID.randomUUID(), entity2=UUID.randomUUID();
        UUID user=UUID.randomUUID(), other=UUID.randomUUID(), event=UUID.randomUUID();
        UUID evidence1=UUID.randomUUID(), evidence2=UUID.randomUUID();
        UUID a1=UUID.randomUUID(), a2=UUID.randomUUID(), a3=UUID.randomUUID();
        Fixture fixture = new Fixture(source, entity1, entity2, user, other, event, evidence1,
                evidence2, a1, a2, a3);
        try {
            createEntity(entity1, "One");
            createEntity(entity2, "Two");
            createUser(user, "BriefA");
            createUser(other, "BriefB");
            jdbc.update("INSERT INTO source(id,source_type,name) VALUES(?,'REGULATOR','Briefing test')", source);
            jdbc.update("""
                INSERT INTO event(id,event_type,title,occurred_at,first_observed_at,last_observed_at,
                                  status,created_at,updated_at,ingestion_origin)
                VALUES(?,'EARNINGS','Briefing integrity',?, ?,?,'CONFIRMED',?,?,'LIVE')
                    """, event, utc(DAY_2.minusSeconds(100)), utc(DAY_2.minusSeconds(100)),
                    utc(DAY_2.minusSeconds(100)), utc(DAY_2.minusSeconds(100)),
                    utc(DAY_2.minusSeconds(100)));
            for (UUID entity : List.of(entity1, entity2)) {
                jdbc.update("""
                    INSERT INTO event_entity(event_id,entity_id,relation_type,relevance)
                    VALUES(?,?,'SUBJECT','HIGH')
                    """, event, entity);
                jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,created_at,updated_at)
                    VALUES(gen_random_uuid(),?,?,?,?)
                    """, user, entity, utc(DAY_2.minusSeconds(1_000)),
                        utc(DAY_2.minusSeconds(1_000)));
            }
            insertEvidence(evidence1, source, "E-1");
            insertEvidence(evidence2, source, "E-2");
            insertAssessment(a1, event, null, "rule-v1", DAY_2.minusSeconds(100));
            linkEvidence(a1, evidence1);
            return fixture;
        } catch (RuntimeException failure) {
            cleanup(fixture);
            throw failure;
        }
    }

    private void createEntity(UUID id, String suffix) {
        jdbc.update("""
                INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active,created_at,updated_at)
                VALUES(?,'COMPANY',?,?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, "Briefing "+suffix, "COMPANY:"+id);
    }

    private void createUser(UUID id, String prefix) {
        String nickname=prefix+id.toString().substring(0,8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,created_at,updated_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, nickname, nickname.toLowerCase(java.util.Locale.ROOT));
    }

    private void insertEvidence(UUID id, UUID source, String externalId) {
        jdbc.update("""
                INSERT INTO evidence(id,source_id,evidence_type,external_id,original_url,content_hash,
                                     collected_at,revision,status)
                VALUES(?,?,'DISCLOSURE',?,?,decode(replace(?::text,'-',''),'hex'),CURRENT_TIMESTAMP,1,'ACTIVE')
                """, id, source, externalId, "https://briefing.test/"+externalId, UUID.randomUUID());
    }

    private void insertAssessment(UUID id, UUID event, UUID predecessor, String version,
            Instant completedAt) {
        jdbc.update("""
                INSERT INTO assessment(id,event_id,analysis_version,method,importance,summary,
                                       confidence,uncertainty,time_horizon,status,input_fingerprint,
                                       completed_at,supersedes_assessment_id,created_at,updated_at)
                VALUES(?, ?,?,'RULE','MEDIUM',?,'HIGH','Known uncertainty','SHORT_TERM',
                       'COMPLETED',decode(replace(?::text,'-',''),'hex'),?,?,?,?)
                """, id, event, version, "Summary "+version, UUID.randomUUID(), utc(completedAt),
                predecessor, utc(completedAt), utc(completedAt));
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

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM briefing_item WHERE assessment_id IN (?,?,?)", f.a1(),f.a2(),f.a3());
        jdbc.update("DELETE FROM briefing WHERE user_id=?", f.user());
        jdbc.update("DELETE FROM user_interest WHERE user_id IN (?,?)", f.user(),f.otherUser());
        jdbc.update("DELETE FROM assessment_evidence WHERE assessment_id IN (?,?,?)", f.a1(),f.a2(),f.a3());
        jdbc.update("DELETE FROM assessment WHERE id IN (?,?,?)", f.a3(),f.a2(),f.a1());
        jdbc.update("DELETE FROM event_entity WHERE event_id=?", f.event());
        jdbc.update("DELETE FROM evidence WHERE id IN (?,?)", f.evidence1(),f.evidence2());
        jdbc.update("DELETE FROM event WHERE id=?", f.event());
        jdbc.update("DELETE FROM entity WHERE id IN (?,?)", f.entity1(),f.entity2());
        jdbc.update("DELETE FROM source WHERE id=?", f.source());
        jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", f.user(),f.otherUser());
    }

    private record Fixture(UUID source, UUID entity1, UUID entity2, UUID user, UUID otherUser,
            UUID event, UUID evidence1, UUID evidence2, UUID a1, UUID a2, UUID a3) {}
}
