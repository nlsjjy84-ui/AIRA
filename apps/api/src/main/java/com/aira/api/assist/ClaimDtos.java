package com.aira.api.assist;

import java.util.List;

/** Request/response shapes for "is this statement true?" checks against official facts. */
public final class ClaimDtos {
    private ClaimDtos() {}

    public record ClaimRequest(String text) {}

    /**
     * verdict: MATCH (same as the official value), APPROX (differs by about one rounding step),
     * MISMATCH (official value differs), NO_DATA (AIRA has no official value for it),
     * NOT_CHECKABLE (opinion, forecast, cause or an item outside the official facts AIRA holds).
     */
    public record ClaimResult(String claim, String verdict, String company, String item, Integer year,
            String stated, String official, String period, String receiptNumber, String evidenceUrl, String note,
            String priorReceiptNumber, String priorEvidenceUrl) {
        public ClaimResult(String claim, String verdict, String company, String item, Integer year, String stated,
                String official, String period, String receiptNumber, String evidenceUrl, String note) {
            this(claim, verdict, company, item, year, stated, official, period, receiptNumber, evidenceUrl, note, null, null);
        }
    }

    public record ClaimResponse(String method, List<ClaimResult> results, String providerKey, List<String> limitations) {}
}
