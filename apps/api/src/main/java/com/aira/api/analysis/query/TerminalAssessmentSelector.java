package com.aira.api.analysis.query;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TerminalAssessmentSelector {
    private TerminalAssessmentSelector() {}

    public static <T extends Candidate> T select(List<T> candidates) {
        Map<UUID, T> byId = new HashMap<>();
        for (T candidate : candidates) {
            if (candidate.assessmentId() == null || candidate.eventId() == null
                    || byId.putIfAbsent(candidate.assessmentId(), candidate) != null
                    || !candidate.eventId().equals(candidates.getFirst().eventId())) return null;
        }
        Set<UUID> candidateIds = candidates.stream().map(Candidate::assessmentId)
                .collect(Collectors.toSet());
        if (candidates.stream().anyMatch(candidate -> candidate.supersedesAssessmentId() != null
                && (!candidateIds.contains(candidate.supersedesAssessmentId())
                        || candidate.predecessorEventId() == null
                        || !candidate.eventId().equals(candidate.predecessorEventId())))) {
            return null;
        }
        Set<UUID> validated = new HashSet<>();
        for (T candidate : candidates) {
            Set<UUID> path = new HashSet<>();
            T node = candidate;
            while (node != null && !validated.contains(node.assessmentId())) {
                if (!path.add(node.assessmentId())) return null;
                node = byId.get(node.supersedesAssessmentId());
            }
            validated.addAll(path);
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
