package com.aira.api.market.opendart;

import java.time.LocalDate;
import com.aira.api.market.domain.EventOrigin;

public record OpenDartDs005Request(String endpointKey, String corpCode, LocalDate beginningDate,
        LocalDate endingDate, EventOrigin origin) {
    public OpenDartDs005Request(String endpointKey, String corpCode, LocalDate beginningDate, LocalDate endingDate) {
        this(endpointKey, corpCode, beginningDate, endingDate, EventOrigin.LEGACY_UNKNOWN);
    }
    public OpenDartDs005Request {
        OpenDartDs005Catalog.approved(endpointKey);
        if (corpCode == null || !corpCode.matches("[0-9]{8}") || beginningDate == null || endingDate == null
                || beginningDate.isBefore(LocalDate.of(2015, 1, 1)) || beginningDate.isAfter(endingDate)
                || origin == null)
            throw new IllegalArgumentException("DS005 requires canonical corp code and explicit valid date window");
    }
}
