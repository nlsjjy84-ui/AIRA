package com.aira.api.market.ecos;

import java.util.ArrayList;
import java.util.List;

public final class EcosPagePlanner {
    private static final long PAGE_SIZE = 1000;

    public EcosPageRange firstPage() {
        return new EcosPageRange(1, PAGE_SIZE);
    }

    public List<EcosPageRange> remainingPages(long totalCount) {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        if (totalCount <= PAGE_SIZE) {
            return List.of();
        }

        var ranges = new ArrayList<EcosPageRange>();
        for (long start = PAGE_SIZE + 1; start <= totalCount; start += PAGE_SIZE) {
            long end = Math.min(start + PAGE_SIZE - 1, totalCount);
            ranges.add(new EcosPageRange(start, end));
        }
        return List.copyOf(ranges);
    }
}
