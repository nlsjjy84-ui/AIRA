package com.aira.api.market.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CanonicalReadResponse<T>(CanonicalDataState state, String selection,
        UUID targetId, String targetType, LocalDate periodStart, LocalDate periodEnd,
        String receipt, T value) {}
