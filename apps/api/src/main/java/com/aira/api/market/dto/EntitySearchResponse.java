package com.aira.api.market.dto;

import com.aira.api.market.domain.EntityType;
import java.util.List;
import java.util.UUID;

public record EntitySearchResponse(CanonicalDataState state, List<Item> entities) {
    public EntitySearchResponse { entities = List.copyOf(entities); }
    public record Item(UUID entityId, EntityType entityType, String canonicalKey,
            String canonicalName, String marketCode, String symbol, String externalIdentifier) {}
}
