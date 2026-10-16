package com.aira.api.market.opendart;

public record OpenDartAnnualCfsContext(
        String corpCode,
        int businessYear,
        String reportCode,
        String financialStatementDivision) {

    public OpenDartAnnualCfsContext {
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        if (businessYear < 1900 || businessYear > 9999) {
            throw new IllegalArgumentException("Business year is invalid");
        }
        // CFS(연결)가 기본이고, 연결재무제표를 내지 않는 회사만 OFS(별도)를 쓴다.
        if (!"11011".equals(reportCode)
                || !("CFS".equals(financialStatementDivision) || "OFS".equals(financialStatementDivision))) {
            throw new IllegalArgumentException("Only OpenDART annual CFS or OFS context is supported");
        }
    }
}
