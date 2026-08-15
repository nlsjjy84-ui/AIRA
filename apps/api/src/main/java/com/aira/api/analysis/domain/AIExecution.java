package com.aira.api.analysis.domain;

import com.aira.api.market.domain.Event;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_execution")
public class AIExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_key", nullable = false, length = 64)
    private String providerKey;

    @Column(name = "model_key", nullable = false, length = 128)
    private String modelKey;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @Column(name = "input_fingerprint", nullable = false)
    private byte[] inputFingerprint;

    @Column(name = "cache_key", nullable = false)
    private byte[] cacheKey;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reused_execution_id")
    private AIExecution reusedExecution;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "estimated_cost", precision = 18, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 18, scale = 8)
    private BigDecimal actualCost;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AIExecutionStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected AIExecution() {}

    public UUID getId() { return id; }
    public String getProviderKey() { return providerKey; }
    public String getModelKey() { return modelKey; }
    public String getTaskType() { return taskType; }
    public String getPromptVersion() { return promptVersion; }
    public Event getEvent() { return event; }
    public Assessment getAssessment() { return assessment; }
    public byte[] getInputFingerprint() { return inputFingerprint == null ? null : inputFingerprint.clone(); }
    public byte[] getCacheKey() { return cacheKey == null ? null : cacheKey.clone(); }
    public boolean isCacheHit() { return cacheHit; }
    public AIExecution getReusedExecution() { return reusedExecution; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public String getCurrencyCode() { return currencyCode; }
    public Integer getLatencyMs() { return latencyMs; }
    public AIExecutionStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
