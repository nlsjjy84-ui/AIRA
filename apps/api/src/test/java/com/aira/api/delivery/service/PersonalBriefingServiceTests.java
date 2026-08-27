package com.aira.api.delivery.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.delivery.dto.BriefingResponse;
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

        assertTrue(sql.contains("en.entity_type='COMPANY'"));
        assertTrue(sql.contains("ev.status='CONFIRMED'"));
        assertFalse(sql.contains("CANDIDATE"));
        assertTrue(sql.contains("a.status='COMPLETED'"));
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM assessment_evidence"));
        assertTrue(sql.contains("a.completed_at>GREATEST(?,ui.created_at)"));
        assertTrue(sql.contains("a.completed_at<=?"));
        assertTrue(sql.contains("ev.occurred_at DESC NULLS LAST,a.id ASC"));
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
        BriefingResponse response = BriefingResponse.empty("ON_DEMAND", START, CUTOFF, CUTOFF);

        assertEquals("ON_DEMAND", response.briefingType());
        assertEquals(START, response.periodStart());
        assertEquals(CUTOFF, response.periodEnd());
        assertEquals(CUTOFF, response.generatedAt());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void noInterestEmptyResponseDoesNotInventACatchUpStart() {
        BriefingResponse response = BriefingResponse.empty("ON_DEMAND", null, CUTOFF, CUTOFF);

        assertNull(response.periodStart());
        assertEquals(CUTOFF, response.periodEnd());
        assertEquals(CUTOFF, response.generatedAt());
    }

    @Test
    void historicalReadUsesStoredAssessmentAndOwnedHeaderWithoutRejoiningInterest() {
        assertTrue(PersonalBriefingService.HEADER_SQL.contains("id=? AND user_id=?"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains(
                "JOIN assessment a ON a.id=bi.assessment_id"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("ev.title"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("ev.occurred_at"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("a.summary"));
        assertTrue(PersonalBriefingService.ITEM_SQL.contains("e.original_url"));
        assertFalse(PersonalBriefingService.ITEM_SQL.contains("user_interest"));
    }

    @Test
    void productionClockContractIsAnAbsoluteUtcInstant() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);

        assertEquals(CUTOFF, OffsetDateTime.now(clock));
    }
}
