package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.delivery.dto.RelatedCompany;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InAppAlertServiceTests {
    private static final UUID USER_A = id(100);
    private static final UUID USER_B = id(200);
    private static final UUID EVENT_A = id(10);
    private static final UUID EVENT_B = id(20);
    private static final UUID ASSESSMENT_A = id(1);
    private static final UUID ASSESSMENT_B = id(2);
    private static final OffsetDateTime ACTIVATED =
            OffsetDateTime.parse("2026-09-01T00:00:00Z");
    private static final OffsetDateTime COMPLETED =
            OffsetDateTime.parse("2026-09-01T00:00:01Z");

    @Test
    void candidateQueryLoadsWholeCompletedGraphBeforeApplyingTerminalEligibility() {
        String sql = InAppAlertService.CANDIDATE_SQL;

        assertTrue(sql.contains("ev.status='CONFIRMED'"));
        assertTrue(sql.contains("en.active=true"));
        assertTrue(sql.contains("a.status='COMPLETED'"));
        assertTrue(sql.contains("assessment_evidence"));
        assertTrue(sql.contains("MIN(ui.alert_enabled_at) AS activated_at"));
        assertTrue(sql.contains("existing.assessment_id=a.id"));
        assertTrue(sql.contains("existing.policy_version=?"));
        assertFalse(sql.contains("existing_assessment.event_id=ev.id"));
        assertFalse(sql.contains("completed_at>"));
        assertFalse(sql.contains("ORDER BY a.completed_at"));
    }

    @Test
    void insertAndLockUseAssessmentIdentityAndSameUserSerialization() {
        assertTrue(InAppAlertService.USER_LOCK_SQL.endsWith("FOR UPDATE"));
        assertTrue(InAppAlertService.INSERT_SQL.contains(
                "ON CONFLICT (user_id,assessment_id,policy_version) DO NOTHING"));
        assertTrue(InAppAlertService.INSERT_SQL.contains("'SENT'"));
        assertTrue(InAppAlertService.INSERT_SQL.contains("CURRENT_TIMESTAMP"));
    }

    @Test
    void historicalQueriesAreOwnedExactAndReturnEveryEvidenceReference() {
        assertTrue(InAppAlertService.USER_VISIBLE_SQL.contains(
                "al.user_id=? AND al.status='SENT'"));
        assertTrue(InAppAlertService.OWNED_SQL.contains(
                "al.id=? AND al.user_id=? AND al.status='SENT'"));
        assertTrue(InAppAlertService.EVIDENCE_SQL.contains("WHERE ae.assessment_id=?"));
        assertTrue(InAppAlertService.EVIDENCE_SQL.contains("ORDER BY e.id"));
        assertFalse(InAppAlertService.USER_VISIBLE_SQL.contains("DISTINCT ON"));
        assertFalse(InAppAlertService.OWNED_SQL.contains("user_interest"));
        assertFalse(InAppAlertService.OWNED_SQL.contains("TerminalAssessmentSelector"));
        assertFalse(InAppAlertService.COMPANIES_SQL.contains("en.active=true"));
    }

    @Test
    void alertRelatedCompaniesAreDeterministicWithoutASingularRepresentative() {
        RelatedCompany low = new RelatedCompany(id(30), "회사 A");
        RelatedCompany high = new RelatedCompany(id(31), "회사 B");
        assertEquals(List.of(low, high),
                RelatedCompanyOrder.normalize(List.of(high, low, high)));
    }

    @Test
    void repeatedSameAssessmentForOneEventProducesOneCandidate() {
        assertEquals(List.of(new InAppAlertService.Candidate(EVENT_A, ASSESSMENT_A)),
                InAppAlertService.selectCandidates(List.of(
                        candidate(EVENT_A, ASSESSMENT_A, null, null),
                        candidate(EVENT_A, ASSESSMENT_A, null, null))));
    }

    @Test
    void differentCompletedAssessmentsForOneEventDoNotChooseAnAmbiguousTerminal() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null),
                candidate(EVENT_A, ASSESSMENT_B, null, null))).isEmpty());
    }

    @Test
    void sameEventSuccessorIsTheSingleTerminalAssessment() {
        assertEquals(List.of(new InAppAlertService.Candidate(EVENT_A, ASSESSMENT_B)),
                InAppAlertService.selectCandidates(List.of(
                        candidate(EVENT_A, ASSESSMENT_A, null, null),
                        candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A))));
    }

    @Test
    void graphOrderOverridesCompletedTimestampOrder() {
        OffsetDateTime olderTimestamp = ACTIVATED.plusSeconds(1);
        OffsetDateTime newerTimestamp = ACTIVATED.plusSeconds(20);
        assertEquals(List.of(new InAppAlertService.Candidate(EVENT_A, ASSESSMENT_B)),
                InAppAlertService.selectCandidates(List.of(
                        candidate(EVENT_A, ASSESSMENT_A, null, null, newerTimestamp, true, false),
                        candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A,
                                olderTimestamp, true, false))));
    }

    @Test
    void terminalMustBeStrictlyAfterActivation() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null, ACTIVATED, true, false)))
                .isEmpty());
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null,
                        ACTIVATED.minusSeconds(1), true, false))).isEmpty());
    }

    @Test
    void evidenceMissingSuccessorDoesNotReviveEvidenceBackedPredecessor() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null, COMPLETED, true, false),
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A,
                        COMPLETED.plusSeconds(1), false, false))).isEmpty());
    }

    @Test
    void exactAlreadyAlertedTerminalDoesNotResendOrReviveItsPredecessor() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null, COMPLETED, true, true),
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A,
                        COMPLETED.plusSeconds(1), true, true))).isEmpty());
    }

    @Test
    void unalertedSuccessorRemainsEligibleAfterPredecessorAlert() {
        assertEquals(List.of(new InAppAlertService.Candidate(EVENT_A, ASSESSMENT_B)),
                InAppAlertService.selectCandidates(List.of(
                        candidate(EVENT_A, ASSESSMENT_A, null, null, COMPLETED, true, true),
                        candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A,
                                COMPLETED.plusSeconds(1), true, false))));
    }

    @Test
    void crossEventCycleAndAmbiguousTerminalAreRejected() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_B))).isEmpty());
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, ASSESSMENT_B, EVENT_A),
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A))).isEmpty());
    }

    @Test
    void differentEventsEachProduceAnAlertCandidate() {
        assertEquals(List.of(
                new InAppAlertService.Candidate(EVENT_A, ASSESSMENT_A),
                new InAppAlertService.Candidate(EVENT_B, ASSESSMENT_B)),
                InAppAlertService.selectCandidates(List.of(
                        candidate(EVENT_A, ASSESSMENT_A, null, null),
                        candidate(EVENT_B, ASSESSMENT_B, null, null))));
    }

    @Test
    void canonicalAssessmentIdentityProducesRawSha256DedupKeys() {
        assertEquals("interest-new-event-v1", InAppAlertService.POLICY);
        byte[] baseline = InAppAlertService.digest(USER_A, ASSESSMENT_A,
                InAppAlertService.POLICY);
        assertEquals(32, baseline.length);
        assertEquals(HexFormat.of().formatHex(baseline), HexFormat.of().formatHex(
                InAppAlertService.digest(USER_A, ASSESSMENT_A, InAppAlertService.POLICY)));
        assertNotEquals(HexFormat.of().formatHex(baseline), HexFormat.of().formatHex(
                InAppAlertService.digest(USER_A, ASSESSMENT_B, InAppAlertService.POLICY)));
        assertNotEquals(HexFormat.of().formatHex(baseline), HexFormat.of().formatHex(
                InAppAlertService.digest(USER_B, ASSESSMENT_A, InAppAlertService.POLICY)));
        assertNotEquals(HexFormat.of().formatHex(baseline), HexFormat.of().formatHex(
                InAppAlertService.digest(USER_A, ASSESSMENT_A, "other-policy")));
    }

    private static InAppAlertService.AssessmentCandidate candidate(UUID eventId,
            UUID assessmentId, UUID supersedesAssessmentId, UUID predecessorEventId) {
        return candidate(eventId, assessmentId, supersedesAssessmentId, predecessorEventId,
                COMPLETED, true, false);
    }

    private static InAppAlertService.AssessmentCandidate candidate(UUID eventId,
            UUID assessmentId, UUID supersedesAssessmentId, UUID predecessorEventId,
            OffsetDateTime completedAt, boolean evidenceBacked, boolean alreadyAlerted) {
        return new InAppAlertService.AssessmentCandidate(eventId, assessmentId,
                supersedesAssessmentId, predecessorEventId, ACTIVATED, completedAt,
                evidenceBacked, alreadyAlerted);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
