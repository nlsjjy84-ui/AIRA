package com.aira.api.assist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only access to stored official company facts. Nothing here is produced by AI. */
public interface OfficialFactReader {
    record Company(UUID id, String name) {}
    /** status is ONE, NONE or MANY; company is set only for ONE. */
    record Resolved(String status, Company company) {}
    record Fact(BigDecimal value, String currency, LocalDate periodStart, LocalDate periodEnd,
            String receiptNumber, String originalUrl) {}

    Resolved resolve(String name);

    /** Annual fact for the predicate in the given business year, or the latest one when year is null. Null if none. */
    Fact find(UUID companyId, String predicate, Integer year);

    /** Latest business year that has any supported annual fact for the company, or null. */
    Integer latestYear(UUID companyId);
}
