package com.aira.api.analysis.query;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TerminalAssessmentSelector {
    private TerminalAssessmentSelector() {}

    public static <T extends Candidate> T select(List<T> candidates) {
        Set<UUID> candidateIds = candidates.stream().map(Candidate::assessmentId)
                .collect(Collectors.toSet());
        if (candidates.stream().anyMatch(candidate -> candidate.supersedesAssessmentId() != null
                && (!candidateIds.contains(candidate.supersedesAssessmentId())
                        || candidate.predecessorEventId() == null
                        || !candidate.eventId().equals(candidate.predecessorEventId())))) {
            return null;
        }
        Set<UUID> superseded = candidates.stream()
                .filter(candidate -> candidate.supersedesAssessmentId() != null
                        && candidate.eventId().equals(candidate.predecessorEventId()))
                .map(Candidate::supersedesAssessmentId)
                .collect(Collectors.toSet());
        List<T> terminal = candidates.stream()
                .filter(candidate -> !superseded.contains(candidate.assessmentId()))
                .toList();
        return terminal.size() == 1 ? terminal.getFirst() : null;
    }

    public interface Candidate {
        UUID assessmentId();
        UUID eventId();
        UUID supersedesAssessmentId();
        UUID predecessorEventId();
    }
}
