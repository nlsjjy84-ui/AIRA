package com.aira.api.analysis.domain;

import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.EventStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "assessment")
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "analysis_version", nullable = false, length = 64)
    private String analysisVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssessmentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Importance importance;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Confidence confidence;

    @Column(columnDefinition = "text")
    private String uncertainty;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_horizon", nullable = false, length = 24)
    private TimeHorizon timeHorizon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssessmentStatus status;

    @Column(name = "input_fingerprint", nullable = false)
    private byte[] inputFingerprint;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_assessment_id")
    private Assessment supersedesAssessment;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "assessment", fetch = FetchType.LAZY)
    private Set<AssessmentEvidence> evidenceLinks = new HashSet<>();

    @OneToMany(mappedBy = "assessment", fetch = FetchType.LAZY)
    private Set<Impact> impacts = new HashSet<>();

    protected Assessment() {}

    public static Assessment completedRule(Event event, String analysisVersion,
            Importance importance, String summary, Confidence confidence, String uncertainty,
            TimeHorizon timeHorizon, byte[] inputFingerprint, OffsetDateTime now) {
        return completedRule(event, analysisVersion, importance, summary, confidence, uncertainty,
                timeHorizon, inputFingerprint, null, now);
    }

    public static Assessment completedRule(Event event, String analysisVersion,
            Importance importance, String summary, Confidence confidence, String uncertainty,
            TimeHorizon timeHorizon, byte[] inputFingerprint, Assessment supersedesAssessment,
            OffsetDateTime now) {
        if (event == null || event.getId() == null || analysisVersion == null
                || analysisVersion.isBlank() || importance == null || summary == null
                || summary.isBlank() || confidence == null || uncertainty == null
                || uncertainty.isBlank() || timeHorizon == null || inputFingerprint == null
                || inputFingerprint.length == 0 || now == null) {
            throw new IllegalArgumentException("Completed rule assessment values are required");
        }
        if (event.getStatus() != EventStatus.CONFIRMED) {
            throw new IllegalStateException("Completed assessments require a confirmed event");
        }
        if (supersedesAssessment != null) {
            if (supersedesAssessment.getId() == null
                    || supersedesAssessment.getEvent() == null
                    || !event.getId().equals(supersedesAssessment.getEvent().getId())) {
                throw new IllegalArgumentException(
                        "An assessment can only supersede an assessment for the same event");
            }
            Set<Assessment> visited = new HashSet<>();
            for (Assessment predecessor = supersedesAssessment; predecessor != null;
                    predecessor = predecessor.getSupersedesAssessment()) {
                if (!visited.add(predecessor)) {
                    throw new IllegalArgumentException("Assessment supersession cannot contain a cycle");
                }
            }
        }
        Assessment assessment = new Assessment();
        assessment.event = event;
        assessment.analysisVersion = analysisVersion;
        assessment.method = AssessmentMethod.RULE;
        assessment.importance = importance;
        assessment.summary = summary;
        assessment.confidence = confidence;
        assessment.uncertainty = uncertainty;
        assessment.timeHorizon = timeHorizon;
        assessment.status = AssessmentStatus.COMPLETED;
        assessment.inputFingerprint = inputFingerprint.clone();
        assessment.completedAt = now;
        assessment.supersedesAssessment = supersedesAssessment;
        assessment.createdAt = now;
        assessment.updatedAt = now;
        return assessment;
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public String getAnalysisVersion() { return analysisVersion; }
    public AssessmentMethod getMethod() { return method; }
    public Importance getImportance() { return importance; }
    public String getSummary() { return summary; }
    public Confidence getConfidence() { return confidence; }
    public String getUncertainty() { return uncertainty; }
    public TimeHorizon getTimeHorizon() { return timeHorizon; }
    public AssessmentStatus getStatus() { return status; }
    public byte[] getInputFingerprint() { return inputFingerprint == null ? null : inputFingerprint.clone(); }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Assessment getSupersedesAssessment() { return supersedesAssessment; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<AssessmentEvidence> getEvidenceLinks() { return evidenceLinks; }
    public Set<Impact> getImpacts() { return impacts; }
}
