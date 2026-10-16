package com.aira.api.assist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only access to stored official company facts. Nothing here is produced by AI. */
public interface OfficialFactReader {
    record Company(UUID id, String name) {}
    /** status: ONE(하나로 정함) · MANY(같은 이름 여러 곳) · LISTED(종목은 있으나 공식 재무 자료가 아직 없음) · NONE. */
    record Resolved(String status, Company company) {}
    /** basis: CFS(연결) 또는 OFS(별도, 연결재무제표를 내지 않는 회사). */
    record Fact(BigDecimal value, String currency, LocalDate periodStart, LocalDate periodEnd,
            String receiptNumber, String originalUrl, String basis) {
        public Fact(BigDecimal value, String currency, LocalDate periodStart, LocalDate periodEnd,
                String receiptNumber, String originalUrl) {
            this(value, currency, periodStart, periodEnd, receiptNumber, originalUrl, "CFS");
        }

        public boolean separate() { return "OFS".equals(basis); }
    }

    Resolved resolve(String name);

    /** Annual fact for the predicate in the given business year, or the latest one when year is null. Null if none. */
    Fact find(UUID companyId, String predicate, Integer year);

    /** Latest business year that has any supported annual fact for the company, or null. */
    Integer latestYear(UUID companyId);
}
