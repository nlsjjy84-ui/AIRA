package com.aira.api.analysis.query;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerminalAssessmentSelectorTests {
    private static final UUID EVENT = new UUID(0, 100);
    private record Candidate(UUID assessmentId, UUID eventId, UUID supersedesAssessmentId,
            UUID predecessorEventId) implements TerminalAssessmentSelector.Candidate {}
    private Candidate candidate(long id, Long predecessor) {
        return new Candidate(new UUID(0,id), EVENT,
                predecessor == null ? null : new UUID(0,predecessor), predecessor == null ? null : EVENT);
    }
    @Test void disconnectedCycleDoesNotMakeAnUnrelatedRootCurrent() {
        assertNull(TerminalAssessmentSelector.select(List.of(candidate(1,2L), candidate(2,1L), candidate(3,null))));
    }
    @Test void selfCycleAlongsideATerminalIsRejected() {
        assertNull(TerminalAssessmentSelector.select(List.of(candidate(1,1L), candidate(2,null))));
    }
    @Test void duplicateIdentityIsRejected() {
        assertNull(TerminalAssessmentSelector.select(List.of(candidate(1,null),candidate(1,null),candidate(2,1L))));
    }
    @Test void validChainSelectsItsTerminalRegardlessOfInputOrder() {
        var terminal = candidate(3,2L);
        assertSame(terminal, TerminalAssessmentSelector.select(List.of(terminal,candidate(1,null),candidate(2,1L))));
    }
}
