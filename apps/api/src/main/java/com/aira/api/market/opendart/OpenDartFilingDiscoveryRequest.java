package com.aira.api.market.opendart;

import java.time.LocalDate;

/**
 * OpenDART filing discovery의 caller-supplied 탐색 범위다.
 * 날짜/공시유형을 임의 기본값으로 채우지 않아 caller가 정한 탐색 의미를 보존한다.
 */
public record OpenDartFilingDiscoveryRequest(
        String corpCode,
        LocalDate beginningDate,
        LocalDate endingDate,
        String disclosureType,
        String disclosureDetailType) {

    public OpenDartFilingDiscoveryRequest {
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must be exactly 8 digits");
        }
        if (beginningDate != null && endingDate != null && endingDate.isBefore(beginningDate)) {
            throw new IllegalArgumentException("OpenDART filing discovery date range is reversed");
        }
        disclosureType = normalizeOptional(disclosureType);
        disclosureDetailType = normalizeOptional(disclosureDetailType);
    }
    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
