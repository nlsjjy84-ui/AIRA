package com.aira.api.market.opendart;

import java.util.Set;

/** Fills an earlier business year for companies that are already stored. */
public interface PriorYearBackfill {
    enum StopReason { NONE, RATE_LIMIT, AUTHENTICATION, REPEATED_FAILURES, INTERRUPTED }

    record Result(int pendingBefore, int attempted, int prepared, int noData, int failed, StopReason stopReason) {
        public int remaining() {
            return Math.max(0, pendingBefore - prepared - noData - failed);
        }

        public String summary(int businessYear) {
            return "OpenDART prior-year backfill " + businessYear + ": pending=" + pendingBefore + ", attempted=" + attempted
                    + ", prepared=" + prepared + ", skippedNoConsolidatedData=" + noData
                    + ", failed=" + failed + ", remaining=" + remaining() + ", stop=" + stopReason;
        }
    }

    /** Handles at most {@code limit} companies and returns what happened. {@code skipStockCodes} is filled while running. */
    Result run(int businessYear, int limit, long pauseMillis, Set<String> skipStockCodes);
}
