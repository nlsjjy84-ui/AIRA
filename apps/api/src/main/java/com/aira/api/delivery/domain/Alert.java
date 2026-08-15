package com.aira.api.delivery.domain;

import com.aira.api.analysis.domain.Assessment;
import com.aira.api.analysis.domain.Impact;
import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_id")
    private Impact impact;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "dedup_key", nullable = false)
    private byte[] dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Alert() {}

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public Assessment getAssessment() { return assessment; }
    public Impact getImpact() { return impact; }
    public String getPolicyVersion() { return policyVersion; }
    public String getReasonCode() { return reasonCode; }
    public byte[] getDedupKey() { return dedupKey == null ? null : dedupKey.clone(); }
    public AlertStatus getStatus() { return status; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public String getFailureCode() { return failureCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
