package com.aira.api.analysis.query;

import com.aira.api.analysis.dto.HistoricalAssessmentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentAssessmentQuery {
    private final JdbcTemplate jdbc;
    private final HistoricalAssessmentQuery historical;
    public CurrentAssessmentQuery(JdbcTemplate jdbc, HistoricalAssessmentQuery historical) {
        this.jdbc = jdbc;
        this.historical = historical;
    }

    @Transactional(readOnly = true)
    public HistoricalAssessmentResponse find(UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("Event identity is required");
        List<Candidate> candidates = jdbc.query("""
                SELECT a.id,a.event_id,a.supersedes_assessment_id,p.event_id
                FROM assessment a LEFT JOIN assessment p ON p.id=a.supersedes_assessment_id
                WHERE a.event_id=? AND a.status='COMPLETED'
                """, (rs, row) -> new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class)), eventId);
        // A timestamp cannot resolve forks or broken lineage; only one graph terminal is Current.
        Candidate terminal = TerminalAssessmentSelector.select(candidates);
        if (terminal == null) throw new HistoricalAssessmentNotFoundException();
        return historical.find(terminal.assessmentId());
    }

    private record Candidate(UUID assessmentId, UUID eventId, UUID supersedesAssessmentId,
            UUID predecessorEventId) implements TerminalAssessmentSelector.Candidate {}
}
