package com.aira.api.analysis.service;

import com.aira.api.analysis.domain.Assessment;
import com.aira.api.analysis.domain.AssessmentEvidence;
import com.aira.api.analysis.domain.AssessmentEvidenceId;
import com.aira.api.analysis.domain.Confidence;
import com.aira.api.analysis.domain.Importance;
import com.aira.api.analysis.domain.TimeHorizon;
import com.aira.api.analysis.repository.AssessmentEvidenceRepository;
import com.aira.api.analysis.repository.AssessmentRepository;
import com.aira.api.market.domain.EventEvidenceId;
import com.aira.api.market.repository.EventEvidenceRepository;
import com.aira.api.market.repository.EventRepository;
import com.aira.api.market.repository.EvidenceRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleBasedEarningsAssessmentService {
    public static final String VERSION = "official-annual-filing-v1";
    private static final String SUMMARY = "공식 연간 연결재무제표 공시는 해당 회계연도의 재무 결과를 확인하는 기준점입니다.";
    private static final String UNCERTAINTY = "이 공시만으로 향후 실적이나 시장 영향을 판단할 수 없으며, 전기 비교와 후속 공시를 함께 확인해야 합니다.";
    private final EventRepository events;
    private final EvidenceRepository evidence;
    private final EventEvidenceRepository eventEvidence;
    private final AssessmentRepository assessments;
    private final AssessmentEvidenceRepository assessmentEvidence;

    public RuleBasedEarningsAssessmentService(EventRepository events, EvidenceRepository evidence,
            EventEvidenceRepository eventEvidence, AssessmentRepository assessments,
            AssessmentEvidenceRepository assessmentEvidence) {
        this.events = events;
        this.evidence = evidence;
        this.eventEvidence = eventEvidence;
        this.assessments = assessments;
        this.assessmentEvidence = assessmentEvidence;
    }

    @Transactional
    public Assessment assess(UUID eventId, UUID evidenceId) {
        if (!eventEvidence.existsById(new EventEvidenceId(eventId, evidenceId))) {
            throw new IllegalArgumentException("Evidence does not support the event");
        }
        var event = events.findById(eventId).orElseThrow();
        var supportingEvidence = evidence.findById(evidenceId).orElseThrow();
        byte[] fingerprint = fingerprint(eventId, supportingEvidence.getContentHash());
        var assessment = assessments.findByEvent_IdAndAnalysisVersionAndInputFingerprint(
                eventId, VERSION, fingerprint).orElseGet(() -> assessments.saveAndFlush(
                        Assessment.completedRule(event, VERSION, Importance.MEDIUM, SUMMARY,
                                Confidence.MEDIUM, UNCERTAINTY, TimeHorizon.UNSPECIFIED,
                                fingerprint, OffsetDateTime.now(ZoneOffset.UTC))));
        var linkId = new AssessmentEvidenceId(assessment.getId(), evidenceId);
        if (!assessmentEvidence.existsById(linkId)) {
            assessmentEvidence.save(AssessmentEvidence.supports(assessment, supportingEvidence,
                    OffsetDateTime.now(ZoneOffset.UTC)));
        }
        return assessment;
    }

    private static byte[] fingerprint(UUID eventId, byte[] evidenceHash) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(16).putLong(eventId.getMostSignificantBits())
                    .putLong(eventId.getLeastSignificantBits()).array());
            digest.update(evidenceHash);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
