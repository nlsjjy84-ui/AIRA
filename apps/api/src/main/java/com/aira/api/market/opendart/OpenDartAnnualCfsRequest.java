package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;

/**
 * caller가 Filing Discovery에서 선택한 filing identity를 Annual CFS 조회 끝까지 보존한다.
 * expectedReceiptNumber가 없으면 최신/첫 응답으로 대체할 수 있으므로 canonical ingestion을 허용하지 않는다.
 */
public record OpenDartAnnualCfsRequest(
        OpenDartAnnualCfsContext context,
        String expectedReceiptNumber,
        FactPredicate predicate) {

    public OpenDartAnnualCfsRequest {
        if (context == null || predicate == null) {
            throw new IllegalArgumentException("Fact predicate is required");
        }
        if (expectedReceiptNumber == null || !expectedReceiptNumber.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("Expected OpenDART receipt number must be exactly 14 digits");
        }
    }
}
