package com.aira.api.delivery.service;

import com.aira.api.analysis.query.TerminalAssessmentSelector;
import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.EvidenceReference;
import com.aira.api.delivery.dto.AlertResponse.Item;
import com.aira.api.delivery.dto.RelatedCompany;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InAppAlertService {
    static final String POLICY = "interest-new-event-v1";
    static final String USER_LOCK_SQL = "SELECT id FROM app_user WHERE id=? FOR UPDATE";
    static final String INSERT_SQL = """
            INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,status,
            sent_at,created_at,updated_at)
            VALUES(?,?,?,?,'NEW_ASSESSMENT',?,'SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ON CONFLICT (user_id,assessment_id,policy_version) DO NOTHING
            """;
    // Match the user's exact canonical target. A COMPANY Event never inherits a SECURITY interest.
    static final String CANDIDATE_SQL = """
            WITH interested_event AS (
                SELECT ev.id AS event_id,MIN(ui.alert_enabled_at) AS activated_at
                FROM user_interest ui
                JOIN entity en ON en.id=ui.entity_id AND en.entity_type IN ('COMPANY','SECURITY') AND en.active=true
                JOIN event_entity ee ON ee.entity_id=ui.entity_id
                JOIN event ev ON ev.id=ee.event_id AND ev.status='CONFIRMED'
                             AND ev.ingestion_origin='LIVE'
                WHERE ui.user_id=? AND ui.alert_enabled=true
                GROUP BY ev.id
            )
            SELECT ie.event_id,a.id,a.supersedes_assessment_id,predecessor.event_id,
                   ie.activated_at,a.created_at,
                   EXISTS (
                       SELECT 1 FROM assessment_evidence ae
                       WHERE ae.assessment_id=a.id
                   ) AS evidence_backed,
                   EXISTS (
                       SELECT 1 FROM alert existing
                       WHERE existing.user_id=? AND existing.assessment_id=a.id
                         AND existing.policy_version=?
                   ) AS already_alerted
            FROM interested_event ie
            JOIN assessment a ON a.event_id=ie.event_id AND a.status='COMPLETED'
            LEFT JOIN assessment predecessor ON predecessor.id=a.supersedes_assessment_id
            ORDER BY ie.event_id,a.id
            """;
    static final String USER_VISIBLE_SQL = """
            SELECT al.id,ev.id,ev.title,ev.event_type,ev.occurred_at,a.id,
                   al.policy_version,al.reason_code,a.analysis_version,a.method,a.importance,
                   a.summary,a.confidence,a.uncertainty,a.completed_at,al.created_at,al.sent_at
            FROM alert al
            JOIN assessment a ON a.id=al.assessment_id
            JOIN event ev ON ev.id=a.event_id
            WHERE al.user_id=? AND al.status='SENT'
            ORDER BY al.sent_at DESC,al.id ASC
            """;
    static final String OWNED_SQL = """
            SELECT al.id,ev.id,ev.title,ev.event_type,ev.occurred_at,a.id,
                   al.policy_version,al.reason_code,a.analysis_version,a.method,a.importance,
                   a.summary,a.confidence,a.uncertainty,a.completed_at,al.created_at,al.sent_at
            FROM alert al
            JOIN assessment a ON a.id=al.assessment_id
            JOIN event ev ON ev.id=a.event_id
            WHERE al.id=? AND al.user_id=? AND al.status='SENT'
            """;
    static final String EVIDENCE_SQL = """
            SELECT e.id,e.external_id,e.original_url,s.name,e.published_at,e.revision
            FROM assessment_evidence ae
            JOIN evidence e ON e.id=ae.evidence_id
            JOIN source s ON s.id=e.source_id
            WHERE ae.assessment_id=?
            ORDER BY e.id
            """;
    static final String COMPANIES_SQL = """
            SELECT en.id,en.canonical_name FROM event_entity ee
            JOIN entity en ON en.id=ee.entity_id
            WHERE ee.event_id=? AND en.entity_type='COMPANY'
            ORDER BY en.id ASC
            """;
    private static final String INTEREST_STATE_SQL = """
            SELECT count(*),count(*) FILTER (WHERE alert_enabled)
            FROM user_interest WHERE user_id=?
            """;

    private final JdbcTemplate jdbc;

    public InAppAlertService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public AlertResponse reconcile(UUID userId) {
        lockUser(userId);
        List<Candidate> candidates = candidates(userId);
        for (Candidate candidate : candidates) {
            jdbc.update(INSERT_SQL, UUID.randomUUID(), userId, candidate.assessmentId(), POLICY,
                    digest(userId, candidate.assessmentId(), POLICY));
        }
        return findAll(userId, candidates.isEmpty()
                ? "NO_ELIGIBLE_ASSESSMENTS" : "NO_SENT_ALERTS");
    }

    private List<Candidate> candidates(UUID userId) {
        List<AssessmentCandidate> graph = jdbc.query(CANDIDATE_SQL,
                (rs, row) -> new AssessmentCandidate(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getObject(5, OffsetDateTime.class),
                        rs.getObject(6, OffsetDateTime.class), rs.getBoolean(7),
                        rs.getBoolean(8)), userId, userId, POLICY);
        return selectCandidates(graph);
    }

    static List<Candidate> selectCandidates(List<AssessmentCandidate> assessments) {
        Map<UUID, List<AssessmentCandidate>> byEvent = assessments.stream().distinct().collect(
                Collectors.groupingBy(AssessmentCandidate::eventId, LinkedHashMap::new,
                        Collectors.toList()));
        return byEvent.entrySet().stream().map(entry -> {
            AssessmentCandidate terminal = TerminalAssessmentSelector.select(entry.getValue());
            // Origin excludes historical ingestion; Assessment creation, not completion, crosses opt-in.
            return terminal != null && terminal.evidenceBacked() && !terminal.alreadyAlerted()
                    && terminal.assessmentCreatedAt().isAfter(terminal.activatedAt())
                    ? new Candidate(entry.getKey(), terminal.assessmentId()) : null;
        }).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse findAll(UUID userId) {
        return findAll(userId, "NO_SENT_ALERTS");
    }

    private AlertResponse findAll(UUID userId, String fallbackEmptyReason) {
        List<RawItem> rawItems = jdbc.query(USER_VISIBLE_SQL, InAppAlertService::rawItem, userId);
        String emptyReason = rawItems.isEmpty() ? emptyReason(userId, fallbackEmptyReason) : null;
        return new AlertResponse(emptyReason, rawItems.stream().map(this::toItem).toList());
    }

    @Transactional(readOnly = true)
    public Item findOwned(UUID userId, UUID alertId) {
        List<RawItem> matches = jdbc.query(OWNED_SQL, InAppAlertService::rawItem,
                alertId, userId);
        if (matches.size() != 1) throw new AlertNotFoundException();
        return toItem(matches.getFirst());
    }

    private Item toItem(RawItem raw) {
        List<EvidenceReference> evidence = jdbc.query(EVIDENCE_SQL,
                (rs, row) -> new EvidenceReference(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class), rs.getInt(6)),
                raw.assessmentId());
        return new Item(raw.alertId(), companies(raw.eventId()), raw.eventId(),
                raw.eventTitle(), raw.eventType(), raw.occurredAt(), raw.assessmentId(),
                raw.policyVersion(), raw.reasonCode(), raw.analysisVersion(), raw.method(),
                raw.importance(), raw.summary(), raw.confidence(), raw.uncertainty(),
                raw.completedAt(), raw.createdAt(), raw.sentAt(), evidence);
    }

    private static RawItem rawItem(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new RawItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                rs.getString(13), rs.getString(14), rs.getObject(15, OffsetDateTime.class),
                rs.getObject(16, OffsetDateTime.class), rs.getObject(17, OffsetDateTime.class));
    }

    private List<RelatedCompany> companies(UUID eventId) {
        return RelatedCompanyOrder.normalize(jdbc.query(COMPANIES_SQL, (rs, row) ->
                new RelatedCompany(rs.getObject(1, UUID.class), rs.getString(2)), eventId));
    }

    private String emptyReason(UUID userId, String fallback) {
        InterestState state = jdbc.queryForObject(INTEREST_STATE_SQL,
                (rs, row) -> new InterestState(rs.getInt(1), rs.getInt(2)), userId);
        if (state == null || state.total() == 0) return "NO_INTERESTS";
        if (state.enabled() == 0) return "NO_ALERT_ENABLED_INTERESTS";
        return fallback;
    }

    private void lockUser(UUID userId) {
        jdbc.queryForObject(USER_LOCK_SQL, UUID.class, userId);
    }

    static byte[] digest(UUID userId, UUID assessmentId, String policyVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, userId.toString());
            update(digest, assessmentId.toString());
            update(digest, policyVersion);
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
        UUID predecessorEventId, OffsetDateTime activatedAt, OffsetDateTime assessmentCreatedAt,
            boolean evidenceBacked, boolean alreadyAlerted)
            implements TerminalAssessmentSelector.Candidate {}
    record Candidate(UUID eventId, UUID assessmentId) {}
    private record RawItem(UUID alertId, UUID eventId, String eventTitle, String eventType,
            OffsetDateTime occurredAt, UUID assessmentId, String policyVersion, String reasonCode,
            String analysisVersion, String method, String importance, String summary,
            String confidence, String uncertainty, OffsetDateTime completedAt,
            OffsetDateTime createdAt, OffsetDateTime sentAt) {}
    private record InterestState(int total, int enabled) {}
}
