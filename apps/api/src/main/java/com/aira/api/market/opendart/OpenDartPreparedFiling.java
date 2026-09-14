package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.ingestion.SourceAwareEarningsIngestionInput;
import com.aira.api.market.service.SourceRegistration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Only the adapter builds this after validating the value and period representations.
public record OpenDartPreparedFiling(OpenDartAnnualCfsContext context,
        EvidenceRegistration valueEvidence, OpenDartPeriodWitnessResolver.Resolved period,
        List<Metric> metrics) {
    static final SourceRegistration SOURCE = new SourceRegistration(
            SourceType.REGULATOR, "opendart", "OpenDART", "opendart.fss.or.kr");

    public OpenDartPreparedFiling {
        if (context == null || valueEvidence == null || period == null || metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("Validated filing values are required");
        }
        metrics = List.copyOf(metrics);
    }

    public List<SourceAwareEarningsIngestionInput> inputs(UUID companyId, String companyName) {
        return metrics.stream().map(metric -> new SourceAwareEarningsIngestionInput(
                SOURCE, valueEvidence, companyId, period.end(),
                companyName + "가 " + context.businessYear() + " 회계연도 연간 재무결과를 공식 공시했습니다.",
                null, metric.predicate(), metric.amount(), metric.currency(), period.start(), period.end(),
                metric.locator())).toList();
    }

    public record Metric(FactPredicate predicate, BigDecimal amount, String currency, String locator) {}
}
