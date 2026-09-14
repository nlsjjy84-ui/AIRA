package com.aira.api.market.ecos;

import java.util.List;

public record EcosMetadataPage(long totalCount, List<EcosMetadataItem> items) {
    public EcosMetadataPage {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        items = List.copyOf(items);
        if (items.size() > totalCount) {
            throw new IllegalArgumentException("item count exceeds totalCount");
        }
    }
}
