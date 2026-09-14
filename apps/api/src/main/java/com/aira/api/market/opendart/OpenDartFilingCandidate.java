package com.aira.api.market.opendart;

import java.time.LocalDate;

/**
 * Strict validation을 통과한 filing discovery 결과다.
 * receipt identity를 보존하며 correction lineage나 Event 의미는 여기서 추론하지 않는다.
 */
public record OpenDartFilingCandidate(
        String corpClass,
        String corpCode,
        String corpName,
        String stockCode,
        String reportName,
        String receiptNumber,
        String filerName,
        LocalDate receiptDate,
        String remarks) {
}
