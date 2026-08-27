package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InAppAlertServiceTests {
    private static final UUID EVENT_A = id(10);
    private static final UUID EVENT_B = id(20);
    private static final UUID ASSESSMENT_A = id(1);
    private static final UUID ASSESSMENT_B = id(2);

    @Test
    void candidatesRequireExplicitOptInConfirmedCompletedAssessmentAndEvidence() {
        String sql = InAppAlertService.CANDIDATE_SQL;

        assertTrue(sql.contains("ev.status='CONFIRMED'"));
        assertFalse(sql.contains("CANDIDATE"));
        assertTrue(sql.contains("a.status='COMPLETED'"));
        assertTrue(sql.contains("assessment_evidence"));
        assertTrue(sql.contains("ui.alert_enabled=true"));
        assertTrue(sql.contains("MIN(ui.updated_at) AS activated_at"));
        assertTrue(sql.contains("a.completed_at>=ie.activated_at"));
        assertTrue(sql.contains("existing.user_id=ui.user_id"));
        assertTrue(sql.contains("existing_assessment.event_id=ev.id"));
        assertFalse(sql.contains("INSERT INTO assessment"));
        assertFalse(sql.contains("UPDATE alert"));
    }

    @Test
    void userVisibleHistoryIsOwnedSentOnlyAndDeterministicallyNewestFirst() {
        String sql = InAppAlertService.USER_VISIBLE_SQL;

        assertTrue(sql.contains("al.user_id=? AND al.status='SENT'"));
        assertTrue(sql.contains("evidence_original_url,created_at,sent_at"));
        assertTrue(sql.contains("ORDER BY sent_at DESC,alert_id ASC"));
        assertFalse(sql.contains("status IN"));
        assertFalse(sql.contains("ORDER BY alert_id DESC"));
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
    void terminalAssessmentMustItselfBeNewlyAvailableAfterActivation() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_A, null, null),
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_A, false))).isEmpty());
    }

    @Test
    void legacyAlertExclusionIsEventBasedAndDoesNotRewriteHistoricalRows() {
        String sql = InAppAlertService.CANDIDATE_SQL;

        assertTrue(sql.contains("JOIN assessment existing_assessment"));
        assertTrue(sql.contains("existing_assessment.event_id=ev.id"));
        assertFalse(sql.contains("existing.policy_version"));
        assertFalse(sql.contains("UPDATE alert"));
        assertFalse(sql.contains("DELETE FROM alert"));
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
    void eventIdentityDefinesTheFinalV1PolicyAndDedupKey() {
        assertEquals("interest-new-event-v1", InAppAlertService.POLICY);
        assertEquals(java.util.HexFormat.of().formatHex(InAppAlertService.digest(EVENT_A)),
                java.util.HexFormat.of().formatHex(InAppAlertService.digest(EVENT_A)));
        assertNotEquals(java.util.HexFormat.of().formatHex(InAppAlertService.digest(EVENT_A)),
                java.util.HexFormat.of().formatHex(InAppAlertService.digest(EVENT_B)));
    }

    @Test
    void crossEventSupersessionDoesNotCreateAnAlertCandidate() {
        assertTrue(InAppAlertService.selectCandidates(List.of(
                candidate(EVENT_A, ASSESSMENT_B, ASSESSMENT_A, EVENT_B))).isEmpty());
    }

    private static InAppAlertService.AssessmentCandidate candidate(UUID eventId,
            UUID assessmentId, UUID supersedesAssessmentId, UUID predecessorEventId) {
        return candidate(eventId, assessmentId, supersedesAssessmentId, predecessorEventId, true);
    }

    private static InAppAlertService.AssessmentCandidate candidate(UUID eventId,
            UUID assessmentId, UUID supersedesAssessmentId, UUID predecessorEventId,
            boolean eligibleAfterActivation) {
        return new InAppAlertService.AssessmentCandidate(eventId, assessmentId,
                supersedesAssessmentId, predecessorEventId, eligibleAfterActivation);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
