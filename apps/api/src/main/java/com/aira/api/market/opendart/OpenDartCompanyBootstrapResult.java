package com.aira.api.market.opendart;

import java.util.UUID;

public record OpenDartCompanyBootstrapResult(
        OpenDartCompanyBootstrapStatus status,
        UUID entityId,
        String corpCode,
        OpenDartCompanyDirectoryRecord officialRecord,
        UUID identifierId,
        boolean existingMapping) {

    public OpenDartCompanyBootstrapResult {
        if (status == null || entityId == null || corpCode == null) {
            throw new IllegalArgumentException("Bootstrap operation result is incomplete");
        }
    }
}
