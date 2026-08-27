package com.aira.api.analysis.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.analysis.dto.CompanyEventExperienceResponse.AssessmentExperience;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyEventExperienceQueryTests {
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OTHER_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final AssessmentExperience FIRST = assessment("LOW", "LOW");
    private static final AssessmentExperience SECOND = assessment("CRITICAL", "HIGH");

    @Test
    void exposesConfirmedEventsOnlyWithoutHidingEventsThatHaveNoAssessment() {
        assertTrue(CompanyEventExperienceQuery.EVENT_SQL.contains("ev.status = 'CONFIRMED'"));
        assertFalse(CompanyEventExperienceQuery.EVENT_SQL.contains("CANDIDATE"));
        assertFalse(CompanyEventExperienceQuery.EVENT_SQL.contains("JOIN assessment"));
    }

    @Test
    void currentCandidatesContainEveryCompletedAssessmentBeforeEvidenceEligibility() {
        String sql = CompanyEventExperienceQuery.CURRENT_ASSESSMENT_SQL;

        assertTrue(sql.contains("a.event_id = ? AND a.status = 'COMPLETED'"));
        assertTrue(sql.contains("assessment_evidence"));
        assertTrue(sql.contains("EXISTS"));
        assertFalse(sql.contains("ae.evidence_id = ?"));
        assertTrue(sql.contains("a.supersedes_assessment_id"));
        assertTrue(sql.contains("LEFT JOIN assessment predecessor"));
        assertFalse(sql.contains("completed_at DESC"));
        assertFalse(sql.contains("importance DESC"));
        assertFalse(sql.contains("confidence DESC"));
    }

    @Test
    void exposesExactlyOneTerminalCompletedAssessment() {
        assertSame(FIRST, CompanyEventExperienceQuery.selectCurrentAssessment(
                List.of(candidate(A, null, null, FIRST))));
    }

    @Test
    void completedSuccessorReplacesItsCompletedPredecessor() {
        assertSame(SECOND, CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(A, null, null, FIRST), candidate(B, A, EVENT, SECOND))));
    }

    @Test
    void terminalUsingDifferentEvidenceStillReplacesEvidenceBackedPredecessor() {
        assertSame(SECOND, CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(A, null, null, FIRST, true),
                candidate(B, A, EVENT, SECOND, true))));
    }

    @Test
    void evidenceMissingTerminalDoesNotFallBackToPredecessor() {
        assertNull(CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(A, null, null, FIRST, true),
                candidate(B, A, EVENT, SECOND, false))));
    }

    @Test
    void keepsTheOfficialEventWithoutAssessmentForZeroOrAmbiguousTerminals() {
        assertNull(CompanyEventExperienceQuery.selectCurrentAssessment(List.of()));
        assertNull(CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(A, null, null, FIRST), candidate(B, null, null, SECOND))));
    }

    @Test
    void importanceAndConfidenceDoNotResolveAmbiguity() {
        assertNull(CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(B, null, null, SECOND), candidate(A, null, null, FIRST))));
        assertEquals("CRITICAL", SECOND.importance());
        assertEquals("HIGH", SECOND.confidence());
    }

    @Test
    void crossEventSupersessionIsTreatedAsAmbiguous() {
        assertNull(CompanyEventExperienceQuery.selectCurrentAssessment(List.of(
                candidate(A, null, null, FIRST), candidate(B, A, OTHER_EVENT, SECOND))));
    }

    @Test
    void draftSuccessorsAreAbsentFromCompletedCandidatesAndDoNotRemoveTheCurrentAssessment() {
        assertSame(FIRST, CompanyEventExperienceQuery.selectCurrentAssessment(
                List.of(candidate(A, null, null, FIRST))));
    }

    private static AssessmentExperience assessment(String importance, String confidence) {
        return new AssessmentExperience(importance, "summary", confidence, "uncertainty",
                "UNSPECIFIED", "RULE");
    }

    private static CompanyEventExperienceQuery.AssessmentCandidate candidate(UUID id,
            UUID supersedesId, UUID predecessorEventId, AssessmentExperience assessment) {
        return candidate(id, supersedesId, predecessorEventId, assessment, true);
    }

    private static CompanyEventExperienceQuery.AssessmentCandidate candidate(UUID id,
            UUID supersedesId, UUID predecessorEventId, AssessmentExperience assessment,
            boolean evidenceBacked) {
        return new CompanyEventExperienceQuery.AssessmentCandidate(
                id, EVENT, supersedesId, predecessorEventId, assessment, evidenceBacked);
    }
}
