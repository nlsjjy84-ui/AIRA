package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.BriefingResponse;
import com.aira.api.delivery.dto.BriefingResponse.EvidenceReference;
import com.aira.api.delivery.dto.BriefingResponse.Item;
import com.aira.api.delivery.dto.RelatedCompany;
import com.aira.api.analysis.query.TerminalAssessmentSelector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalBriefingService {
    static final String POLICY_VERSION = "interest-assessment-v1";
    static final String BRIEFING_TYPE = "ON_DEMAND";
    static final String USER_LOCK_SQL = "SELECT id FROM app_user WHERE id=? FOR UPDATE";
    static final String HEADER_SQL = """
            SELECT id,title,status,briefing_type,period_start,period_end,generated_at FROM briefing
            WHERE id=? AND user_id=?
            """;
    static final String ITEM_SQL = """
            SELECT bi.display_order,ev.id,ev.event_type,ev.title,ev.occurred_at,
                   a.id,a.analysis_version,a.summary,a.uncertainty,a.importance,a.confidence,
                   a.completed_at
            FROM briefing_item bi
            JOIN assessment a ON a.id=bi.assessment_id
            JOIN event ev ON ev.id=a.event_id
            WHERE bi.briefing_id=?
            ORDER BY bi.display_order
            """;
    static final String EVIDENCE_SQL = """
            SELECT bi.assessment_id,e.id,e.external_id,e.original_url,s.name
            FROM briefing_item bi
            JOIN assessment_evidence ae ON ae.assessment_id=bi.assessment_id
            JOIN evidence e ON e.id=ae.evidence_id
            JOIN source s ON s.id=e.source_id
            WHERE bi.briefing_id=?
            ORDER BY bi.display_order,e.id
            """;
    static final String COMPANIES_SQL = """
            SELECT en.id,en.canonical_name FROM event_entity ee
            JOIN entity en ON en.id=ee.entity_id
            WHERE ee.event_id=? AND en.entity_type='COMPANY' AND en.active=true
            ORDER BY en.id ASC
            """;
    static final String CANDIDATE_SQL = """
            WITH interested_event AS (
                SELECT ev.id AS event_id,MIN(ui.created_at) AS activated_at
                FROM user_interest ui
                JOIN entity en ON en.id=ui.entity_id AND en.entity_type='COMPANY'
                JOIN event_entity ee ON ee.entity_id=ui.entity_id
                JOIN event ev ON ev.id=ee.event_id AND ev.status='CONFIRMED'
                WHERE ui.user_id=?
                GROUP BY ev.id
            )
            SELECT ie.event_id,a.id,a.supersedes_assessment_id,predecessor.event_id,
                   ev.occurred_at,a.completed_at,GREATEST(?,ie.activated_at),
                   EXISTS (SELECT 1 FROM assessment_evidence ae WHERE ae.assessment_id=a.id)
            FROM interested_event ie
            JOIN event ev ON ev.id=ie.event_id
            JOIN assessment a ON a.event_id=ie.event_id AND a.status='COMPLETED'
                             AND a.completed_at<=?
            LEFT JOIN assessment predecessor ON predecessor.id=a.supersedes_assessment_id
            ORDER BY ie.event_id,a.id
            """;
    static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparing(Candidate::occurredAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Candidate::assessmentId);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PersonalBriefingService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    PersonalBriefingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public BriefingResponse getOrCreate(UUID userId) {
        lockUser(userId);
        OffsetDateTime cutoff = OffsetDateTime.now(clock);
        OffsetDateTime periodStart = periodStart(userId, cutoff);
        if (periodStart == null) {
            return BriefingResponse.empty(
                    BRIEFING_TYPE, null, cutoff, OffsetDateTime.now(clock), "NO_INTERESTS");
        }
        if (!periodStart.isBefore(cutoff)) {
            UUID existing = findAtPeriodEnd(userId, cutoff);
            if (existing != null) return findOwned(userId, existing);
            return BriefingResponse.empty(
                    BRIEFING_TYPE, periodStart, cutoff, OffsetDateTime.now(clock),
                    "NO_ELIGIBLE_ASSESSMENTS");
        }

        List<Candidate> candidates = candidates(userId, periodStart, cutoff);
        if (candidates.isEmpty()) {
            UUID existing = findLatestCompleted(userId, cutoff);
            if (existing != null) return findOwned(userId, existing);
            return BriefingResponse.empty(
                    BRIEFING_TYPE, periodStart, cutoff, OffsetDateTime.now(clock),
                    "NO_ELIGIBLE_ASSESSMENTS");
        }

        byte[] dedupKey = digest(periodStart, cutoff,
                candidates.stream().map(candidate -> candidate.assessmentId().toString()).toList());
        UUID briefingId = findByDedup(userId, dedupKey);
        if (briefingId == null) {
            briefingId = create(userId, periodStart, cutoff, candidates, dedupKey);
        }
        return findOwned(userId, briefingId);
    }

    @Transactional(readOnly = true)
    public BriefingResponse findOwned(UUID userId, UUID briefingId) {
        var headers = jdbc.query(HEADER_SQL,
                (rs, row) -> new Header(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                        rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class)),
                briefingId, userId);
        if (headers.isEmpty()) throw new BriefingNotFoundException();
        Header header = headers.getFirst();
        List<RawItem> rawItems = jdbc.query(ITEM_SQL,
                (rs, row) -> new RawItem(rs.getShort(1), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                        rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11),
                        rs.getObject(12, OffsetDateTime.class)), briefingId);
        Map<UUID, List<EvidenceReference>> evidenceByAssessment = jdbc.query(EVIDENCE_SQL,
                (rs, row) -> new AssessmentEvidence(rs.getObject(1, UUID.class),
                        new EvidenceReference(rs.getObject(2, UUID.class), rs.getString(3),
                                rs.getString(4), rs.getString(5))), briefingId).stream()
                .collect(java.util.stream.Collectors.groupingBy(AssessmentEvidence::assessmentId,
                        LinkedHashMap::new, java.util.stream.Collectors.mapping(
                                AssessmentEvidence::evidence, java.util.stream.Collectors.toList())));
        List<Item> items = rawItems.stream().map(raw -> new Item(raw.displayOrder(),
                companies(raw.eventId()), raw.eventId(), raw.eventType(), raw.eventTitle(),
                raw.occurredAt(), raw.assessmentId(), raw.analysisVersion(), raw.summary(),
                raw.uncertainty(), raw.importance(), raw.confidence(), raw.completedAt(),
                evidenceByAssessment.getOrDefault(raw.assessmentId(), List.of()))).toList();
        return new BriefingResponse(header.id(), header.title(), header.status(), header.briefingType(),
                header.periodStart(), header.periodEnd(), header.generatedAt(), null, items);
    }

    private List<RelatedCompany> companies(UUID eventId) {
        return RelatedCompanyOrder.normalize(jdbc.query(COMPANIES_SQL, (rs, row) ->
                new RelatedCompany(rs.getObject(1, UUID.class), rs.getString(2)), eventId));
    }

    private void lockUser(UUID userId) {
        jdbc.queryForObject(USER_LOCK_SQL, UUID.class, userId);
    }

    private OffsetDateTime periodStart(UUID userId, OffsetDateTime cutoff) {
        List<OffsetDateTime> previous = jdbc.query("""
                SELECT period_end FROM briefing
                WHERE user_id=? AND briefing_type=? AND status IN ('READY','DELIVERED')
                  AND period_end<=?
                ORDER BY period_end DESC LIMIT 1
                """, (rs, row) -> rs.getObject(1, OffsetDateTime.class), userId, BRIEFING_TYPE,
                Timestamp.from(cutoff.toInstant()));
        List<OffsetDateTime> earliestInterest = jdbc.query("""
                SELECT min(ui.created_at) FROM user_interest ui
                JOIN entity en ON en.id=ui.entity_id AND en.entity_type='COMPANY'
                WHERE ui.user_id=?
                HAVING count(*)>0
                """, (rs, row) -> rs.getObject(1, OffsetDateTime.class), userId);
        return selectPeriodStart(previous, earliestInterest);
    }

    static OffsetDateTime selectPeriodStart(List<OffsetDateTime> previous,
            List<OffsetDateTime> earliestInterest) {
        if (!previous.isEmpty()) return previous.getFirst();
        return earliestInterest.isEmpty() ? null : earliestInterest.getFirst();
    }

    private List<Candidate> candidates(UUID userId, OffsetDateTime periodStart,
            OffsetDateTime cutoff) {
        List<AssessmentCandidate> graph = jdbc.query(CANDIDATE_SQL, (rs, row) ->
                        new AssessmentCandidate(rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                                rs.getObject(4, UUID.class), rs.getObject(5, OffsetDateTime.class),
                                rs.getObject(6, OffsetDateTime.class),
                                rs.getObject(7, OffsetDateTime.class), rs.getBoolean(8)),
                userId, Timestamp.from(periodStart.toInstant()), Timestamp.from(cutoff.toInstant()));
        return selectCandidates(graph).stream().sorted(CANDIDATE_ORDER).toList();
    }

    static List<Candidate> selectCandidates(List<AssessmentCandidate> assessments) {
        Map<UUID, List<AssessmentCandidate>> byEvent = assessments.stream().distinct().collect(
                java.util.stream.Collectors.groupingBy(AssessmentCandidate::eventId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return byEvent.values().stream().map(eventAssessments -> {
            AssessmentCandidate terminal = TerminalAssessmentSelector.select(eventAssessments);
            return terminal != null && terminal.evidenceBacked()
                    && terminal.completedAt().isAfter(terminal.effectiveStart())
                    ? new Candidate(terminal.assessmentId(), terminal.occurredAt(),
                            terminal.completedAt()) : null;
        }).filter(Objects::nonNull).distinct().toList();
    }

    private UUID create(UUID userId, OffsetDateTime periodStart, OffsetDateTime periodEnd,
            List<Candidate> candidates, byte[] dedupKey) {
        UUID id = UUID.randomUUID();
        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        try {
            jdbc.update("""
                    INSERT INTO briefing(id,user_id,briefing_type,period_start,period_end,policy_version,
                    status,title,dedup_key,generated_at,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,'READY','내 브리핑',?,?,?,?)
                    """, id, userId, BRIEFING_TYPE, Timestamp.from(periodStart.toInstant()),
                    Timestamp.from(periodEnd.toInstant()), POLICY_VERSION, dedupKey,
                    Timestamp.from(generatedAt.toInstant()), Timestamp.from(generatedAt.toInstant()),
                    Timestamp.from(generatedAt.toInstant()));
            short order = 1;
            for (Candidate candidate : candidates) {
                jdbc.update("""
                        INSERT INTO briefing_item(briefing_id,assessment_id,display_order,reason_code,created_at)
                        VALUES(?,?,?,'CURRENT_INTEREST',CURRENT_TIMESTAMP)
                        """, id, candidate.assessmentId(), order++);
            }
            return id;
        } catch (DuplicateKeyException race) {
            UUID existing = findByDedup(userId, dedupKey);
            if (existing != null) return existing;
            throw race;
        }
    }

    private UUID findByDedup(UUID userId, byte[] key) {
        var ids = jdbc.query("SELECT id FROM briefing WHERE user_id=? AND dedup_key=?",
                (rs, row) -> rs.getObject(1, UUID.class), userId, key);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private UUID findAtPeriodEnd(UUID userId, OffsetDateTime periodEnd) {
        var ids = jdbc.query("""
                SELECT id FROM briefing
                WHERE user_id=? AND briefing_type=? AND period_end=?
                  AND status IN ('READY','DELIVERED')
                ORDER BY generated_at DESC,id ASC LIMIT 1
                """, (rs, row) -> rs.getObject(1, UUID.class), userId, BRIEFING_TYPE,
                Timestamp.from(periodEnd.toInstant()));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private UUID findLatestCompleted(UUID userId, OffsetDateTime cutoff) {
        var ids = jdbc.query("""
                SELECT id FROM briefing
                WHERE user_id=? AND briefing_type=? AND period_end<=?
                  AND status IN ('READY','DELIVERED')
                ORDER BY period_end DESC,generated_at DESC,id ASC LIMIT 1
                """, (rs, row) -> rs.getObject(1, UUID.class), userId, BRIEFING_TYPE,
                Timestamp.from(cutoff.toInstant()));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    static byte[] digest(OffsetDateTime periodStart, OffsetDateTime periodEnd,
            List<String> assessmentIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, BRIEFING_TYPE);
            update(digest, POLICY_VERSION);
            update(digest, periodStart.toInstant().toString());
            update(digest, periodEnd.toInstant().toString());
            assessmentIds.forEach(id -> update(digest, id));
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    record AssessmentCandidate(UUID eventId, UUID assessmentId, UUID supersedesAssessmentId,
            UUID predecessorEventId, OffsetDateTime occurredAt, OffsetDateTime completedAt,
            OffsetDateTime effectiveStart, boolean evidenceBacked)
            implements TerminalAssessmentSelector.Candidate {}
    record Candidate(UUID assessmentId, OffsetDateTime occurredAt, OffsetDateTime completedAt) {}
    private record RawItem(short displayOrder, UUID eventId, String eventType, String eventTitle,
            OffsetDateTime occurredAt, UUID assessmentId, String analysisVersion, String summary,
            String uncertainty, String importance, String confidence, OffsetDateTime completedAt) {}
    private record AssessmentEvidence(UUID assessmentId, EvidenceReference evidence) {}
    private record Header(UUID id, String title, String status, String briefingType,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, OffsetDateTime generatedAt) {}
}
