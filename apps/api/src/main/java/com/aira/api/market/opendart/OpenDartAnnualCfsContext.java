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
        if (!"11011".equals(reportCode) || !"CFS".equals(financialStatementDivision)) {
            throw new IllegalArgumentException("Only OpenDART annual CFS context is supported");
        }
    }
}
