package com.aira.api.assist;

import java.util.List;

/** What the screen already shows. Only labels, values and periods; never ids, accounts or personal data. */
public record AssistRequest(String kind, String subject, List<Fact> facts) {
    public record Fact(String label, String value, String period) {}
}
