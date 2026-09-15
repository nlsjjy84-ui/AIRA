package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.delivery.dto.BriefingResponse;
import com.aira.api.delivery.dto.RelatedCompany;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalBriefingServiceTests {
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-08-20T00:00:00Z");
    private static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-08-23T00:00:00Z");

    @Test
    void candidateContractUsesCatchUpReadinessBoundariesAndNoAlertRanking() {
        String sql = PersonalBriefingService.CANDIDATE_SQL;

        assertTrue(sql.contains("en.entity_type IN ('COMPANY','SECURITY')"));
        assertTrue(sql.contains("ev.status='CONFIRMED'"));
        assertFalse(sql.contains("CANDIDATE"));
        assertTrue(sql.contains("a.status='COMPLETED'"));
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM assessment_evidence"));
        assertTrue(sql.contains("MIN(ui.created_at)"));
        assertTrue(sql.contains("GREATEST(?,ie.activated_at)"));
        assertTrue(sql.contains("a.completed_at<=?"));
        assertTrue(sql.contains("a.supersedes_assessment_id"));
        assertTrue(sql.contains("predecessor.event_id"));
        assertFalse(sql.contains("alert_enabled"));
        assertFalse(sql.contains("importance"));
        assertFalse(sql.contains("confidence"));
        assertFalse(sql.contains("interest_level"));
        assertTrue(PersonalBriefingService.USER_LOCK_SQL.endsWith("FOR UPDATE"));
    }

    @Test
    void firstWindowStartsAtEarliestCurrentInterestAndSubsequentWindowAtPreviousEnd() {
        OffsetDateTime interestCreatedAt = OffsetDateTime.parse("2026-08-19T00:00:00Z");

        assertEquals(interestCreatedAt,
                PersonalBriefingService.selectPeriodStart(List.of(), List.of(interestCreatedAt)));
        assertEquals(START,
                PersonalBriefingService.selectPeriodStart(List.of(START), List.of(interestCreatedAt)));
    }

    @Test
    void ordersByOccurrenceDescendingThenAssessmentId() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        OffsetDateTime newest = OffsetDateTime.parse("2026-08-22T00:00:00Z");
        OffsetDateTime older = OffsetDateTime.parse("2026-08-21T00:00:00Z");
        var candidates = new ArrayList<>(List.of(
                new PersonalBriefingService.Candidate(high, newest, CUTOFF),
                new PersonalBriefingService.Candidate(low, newest, CUTOFF),
                new PersonalBriefingService.Candidate(UUID.randomUUID(), older, CUTOFF)));

        candidates.sort(PersonalBriefingService.CANDIDATE_ORDER);

        assertEquals(low, candidates.get(0).assessmentId());
        assertEquals(high, candidates.get(1).assessmentId());
        assertEquals(older, candidates.get(2).occurredAt());
    }

    @Test
    void logicalFingerprintIncludesWindowPolicyTypeAndCandidateOrder() {
        List<String> ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        byte[] first = PersonalBriefingService.digest(START, CUTOFF, ids);

        assertArrayEquals(first, PersonalBriefingService.digest(START, CUTOFF, ids));
        assertNotEquals(java.util.HexFormat.of().formatHex(first), java.util.HexFormat.of().formatHex(
                PersonalBriefingService.digest(START, CUTOFF.plusSeconds(1), ids)));
        assertNotEquals(java.util.HexFormat.of().formatHex(first), java.util.HexFormat.of().formatHex(
                PersonalBriefingService.digest(START, CUTOFF, ids.reversed())));
    }

    @Test
    void emptyResponseExposesAnHonestOnDemandWindowWithoutItems() {
        BriefingResponse response = BriefingResponse.empty("ON_DEMAND", START, CUTOFF, CUTOFF,
                "NO_ELIGIBLE_ASSESSMENTS");

        assertEquals("ON_DEMAND", response.briefingType());
        assertEquals(START, response.periodStart());
        assertEquals(CUTOFF, response.periodEnd());
        assertEquals(CUTOFF, response.generatedAt());
        assertEquals("NO_ELIGIBLE_ASSESSMENTS", response.emptyReason());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void noInterestEmptyResponseDoesNotInventACatchUpStart() {
        BriefingResponse response = BriefingResponse.empty("ON_DEMAND", null, CUTOFF, CUTOFF,
                "NO_INTERESTS");

        assertNull(response.periodStart());
        assertEquals(CUTOFF, response.periodEnd());
        assertEquals(CUTOFF, response.generatedAt());
        assertEquals("NO_INTERESTS", response.emptyReason());
    }

    @Test
    void historicalReadUsesStoredAssessmentAndOwnedHeaderWithoutRejoiningInterest() {
        assertTrue(PersonalBriefingService.HEADER_SQL.contains("id=? AND user_id=?"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains(
                "JOIN assessment a ON a.id=bi.assessment_id"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("ev.title"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("ev.occurred_at"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("a.summary"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("a.analysis_version"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("a.completed_at"));
        assertTrue(PersonalBriefingService.EVIDENCE_SQL.contains("e.id"));
        assertTrue(PersonalBriefingService.EVIDENCE_SQL.contains("e.original_url"));
        assertFalse(PersonalBriefingService.ITEM_SQL.contains("user_interest"));
        assertTrue(PersonalBriefingService.COMPANIES_SQL.contains("en.active=true"));
        assertTrue(PersonalBriefingService.COMPANIES_SQL.contains("ORDER BY en.id ASC"));
    }

    @Test
    void relatedCompaniesAreCompleteDeterministicAndHaveNoArbitraryRepresentative() {
        RelatedCompany low = new RelatedCompany(new UUID(0, 1), "회사 A");
        RelatedCompany high = new RelatedCompany(new UUID(0, 2), "회사 B");

        assertEquals(List.of(low, high),
                RelatedCompanyOrder.normalize(List.of(high, low, high)));
    }

    @Test
    void productionClockContractIsAnAbsoluteUtcInstant() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);

        assertEquals(CUTOFF, OffsetDateTime.now(clock));
    }

    @Test
    void selectsTheCutoffTerminalByGraphRatherThanTimestamp() {
        UUID event = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID a3 = UUID.randomUUID();
        OffsetDateTime eligible = START.plusHours(1);
        var firstCutoff = List.of(
                assessment(event, a1, null, null, eligible.plusDays(2), true),
                assessment(event, a2, a1, event, eligible, true));

        assertEquals(a2, PersonalBriefingService.selectCandidates(firstCutoff)
                .getFirst().assessmentId());

        var secondCutoff = new ArrayList<>(firstCutoff);
        secondCutoff.add(assessment(event, a3, a2, event, eligible.plusDays(1), true));
        assertEquals(a3, PersonalBriefingService.selectCandidates(secondCutoff)
                .getFirst().assessmentId());
    }

    @Test
    void rejectsAmbiguousCrossEventAndCycleGraphs() {
        UUID event = UUID.randomUUID();
        UUID otherEvent = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();

        assertTrue(PersonalBriefingService.selectCandidates(List.of(
                assessment(event, a1, null, null, START.plusHours(1), true),
                assessment(event, a2, null, null, START.plusHours(2), true))).isEmpty());
        assertTrue(PersonalBriefingService.selectCandidates(List.of(
                assessment(event, a1, a2, otherEvent, START.plusHours(1), true))).isEmpty());
        assertTrue(PersonalBriefingService.selectCandidates(List.of(
                assessment(event, a1, a2, event, START.plusHours(1), true),
                assessment(event, a2, a1, event, START.plusHours(2), true))).isEmpty());
    }

    @Test
    void appliesExclusiveEffectiveStartAfterTerminalSelectionAndSuppressesJoinDuplicates() {
        UUID event = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        var boundary = assessment(event, a1, null, null, START, true);
        assertTrue(PersonalBriefingService.selectCandidates(List.of(boundary, boundary)).isEmpty());

        var eligible = assessment(event, a1, null, null, START.plusNanos(1), true);
        assertEquals(1, PersonalBriefingService.selectCandidates(List.of(eligible, eligible)).size());
        assertTrue(PersonalBriefingService.selectCandidates(List.of(
                assessment(event, a1, null, null, START.plusNanos(1), false))).isEmpty());
    }

    private static PersonalBriefingService.AssessmentCandidate assessment(UUID eventId,
            UUID assessmentId, UUID supersedesId, UUID predecessorEventId,
            OffsetDateTime completedAt, boolean evidenceBacked) {
        return new PersonalBriefingService.AssessmentCandidate(eventId, assessmentId,
                supersedesId, predecessorEventId, completedAt, completedAt, START, evidenceBacked);
    }
}
