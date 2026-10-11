package com.aira.api.assist;

import java.util.List;

/** Request/response shapes for filing-quote answers, filing comparison and relation candidates. */
public final class FilingDtos {
    private FilingDtos() {}

    public record QuoteRequest(String receiptNumber, String question) {}
    public record Quote(int passageNo, String text) {}
    public record QuoteResponse(String method, String receiptNumber, List<Quote> quotes, String explanation,
            String providerKey, String modelKey, List<String> limitations) {}

    public record CompareRequest(String originalReceipt, String amendedReceipt) {}
    public record Change(String before, String after) {}
    public record CompareResponse(String method, String originalReceipt, String amendedReceipt,
            List<Change> changes, int totalChanges, boolean truncated, String explanation,
            String providerKey, String modelKey, List<String> limitations) {}

    public record CandidateEvent(String id, String title, String type, String date) {}
    public record CandidateRequest(String subject, List<CandidateEvent> events) {}
    public record Candidate(String firstId, String secondId, String reason) {}
    public record CandidateResponse(String method, List<Candidate> candidates, String providerKey,
            List<String> limitations) {}
}
