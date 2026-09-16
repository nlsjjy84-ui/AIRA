package com.aira.api.personalfinance.service;

import com.aira.api.personalfinance.ai.AiExplanationInput;
import com.aira.api.personalfinance.ai.AiProviderException;
import com.aira.api.personalfinance.ai.AiProviderResult;
import com.aira.api.personalfinance.ai.PersonalFinanceAiProvider;
import com.aira.api.personalfinance.dto.MonthlySpendingPatternResponse;
import com.aira.api.personalfinance.dto.PersonalFinanceExplanationResponse;
import com.aira.api.personalfinance.dto.SpendingChangeResponse;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Adds an optional AI explanation on top of the deterministic RULE result. */
@Service
public class PersonalFinanceAiExplanationService {
    private static final String TASK_TYPE = "PERSONAL_FINANCE_EXPLANATION";
    private static final String PROMPT_VERSION = "pf-explain-v1";
    private final PersonalFinancePatternService patterns;
    private final PersonalFinanceAiProvider provider;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public PersonalFinanceAiExplanationService(PersonalFinancePatternService patterns,
            PersonalFinanceAiProvider provider, JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.patterns = patterns;
        this.provider = provider;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public PersonalFinanceExplanationResponse explain(UUID userId, String month, String currency) {
        MonthlySpendingPatternResponse pattern = patterns.analyze(userId, month, currency);
        if (!provider.available()) {
            return fallback(pattern, "AI provider is not configured; RULE analysis remains available.");
        }

        AiExplanationInput input = toAiInput(pattern);
        byte[] fingerprint = fingerprint(input);
        byte[] cacheKey = hash(provider.providerKey() + "|" + provider.modelKey() + "|"
                + PROMPT_VERSION + "|" + HexFormat.of().formatHex(fingerprint));
        UUID executionId = UUID.randomUUID();
        OffsetDateTime started = OffsetDateTime.now(clock);
        insertRunning(executionId, userId, fingerprint, cacheKey, started);

        long before = System.nanoTime();
        try {
            AiProviderResult result = provider.explain(input);
            int latencyMs = latencyMillis(before);
            markSucceeded(executionId, result, latencyMs, OffsetDateTime.now(clock));
            return success(pattern, executionId, result.text());
        } catch (AiProviderException failure) {
            markFailed(executionId, failure.code(), latencyMillis(before), OffsetDateTime.now(clock));
            return fallback(pattern, "AI explanation failed; RULE analysis remains available.");
        } catch (RuntimeException failure) {
            markFailed(executionId, "PROVIDER_FAILURE", latencyMillis(before), OffsetDateTime.now(clock));
            return fallback(pattern, "AI explanation failed; RULE analysis remains available.");
        }
    }

    private AiExplanationInput toAiInput(MonthlySpendingPatternResponse pattern) {
        SpendingChangeResponse total = pattern.total();
        List<AiExplanationInput.CategoryChange> categories = pattern.categories().stream()
                .map(change -> new AiExplanationInput.CategoryChange(
                        change.scope().name(), change.currentSpent(),
                        change.previousSpent(), change.delta()))
                .toList();
        return new AiExplanationInput(
                pattern.month(), pattern.previousMonth(), pattern.currencyCode(),
                total.currentSpent(), total.previousSpent(), total.delta(), categories);
    }

    private void insertRunning(UUID id, UUID userId, byte[] fingerprint, byte[] cacheKey, OffsetDateTime started) {
        jdbc.update("""
                INSERT INTO ai_execution(
                    id,provider_key,model_key,task_type,prompt_version,personal_finance_user_id,
                    input_fingerprint,cache_key,cache_hit,status,started_at)
                VALUES (?,?,?,?,?,?,?,?,false,'RUNNING',?)
                """, id, provider.providerKey(), provider.modelKey(), TASK_TYPE, PROMPT_VERSION,
                userId, fingerprint, cacheKey, started);
    }

    private void markSucceeded(UUID id, AiProviderResult result, int latencyMs, OffsetDateTime completed) {
        jdbc.update("""
                UPDATE ai_execution
                SET status='SUCCEEDED', input_tokens=?, output_tokens=?,
                    latency_ms=?, completed_at=?, error_code=NULL
                WHERE id=? AND status='RUNNING'
                """, result.inputTokens(), result.outputTokens(), latencyMs, completed, id);
    }

    private void markFailed(UUID id, String code, int latencyMs, OffsetDateTime completed) {
        jdbc.update("""
                UPDATE ai_execution
                SET status='FAILED', latency_ms=?, completed_at=?, error_code=?
                WHERE id=? AND status='RUNNING'
                """, latencyMs, completed, safeCode(code), id);
    }

    private PersonalFinanceExplanationResponse success(
            MonthlySpendingPatternResponse pattern, UUID executionId, String explanation) {
        List<String> limitations = new ArrayList<>(pattern.limitations());
        limitations.add("AI explanation is descriptive and cannot infer the cause of spending changes from aggregates alone.");
        return new PersonalFinanceExplanationResponse(
                "AI", explanation, executionId, provider.providerKey(), provider.modelKey(),
                pattern, List.copyOf(limitations));
    }

    private PersonalFinanceExplanationResponse fallback(
            MonthlySpendingPatternResponse pattern, String reason) {
        List<String> limitations = new ArrayList<>(pattern.limitations());
        limitations.add(reason);
        return new PersonalFinanceExplanationResponse(
                "RULE", null, null, null, null, pattern, List.copyOf(limitations));
    }

    private byte[] fingerprint(AiExplanationInput input) {
        try {
            return hash(json.writeValueAsString(input));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to fingerprint aggregate AI input", failure);
        }
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static int latencyMillis(long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
    }

    private static String safeCode(String value) {
        if (value == null || value.isBlank()) return "PROVIDER_FAILURE";
        String cleaned = value.replaceAll("[^A-Z0-9_-]", "_");
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }
}
