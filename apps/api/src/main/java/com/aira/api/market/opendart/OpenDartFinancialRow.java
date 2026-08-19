package com.aira.api.market.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenDartFinancialRow(
        @JsonProperty("rcept_no") String receiptNumber,
        @JsonProperty("bsns_year") String businessYear,
        @JsonProperty("reprt_code") String reportCode,
        @JsonProperty("corp_code") String corpCode,
        @JsonProperty("fs_div") String financialStatementDivision,
        @JsonProperty("sj_div") String statementDivision,
        @JsonProperty("sj_nm") String statementName,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("account_nm") String accountName,
        @JsonProperty("account_detail") String accountDetail,
        @JsonProperty("thstrm_dt") String currentTerm,
        @JsonProperty("thstrm_nm") String currentTermName,
        @JsonProperty("thstrm_amount") String currentTermAmount,
        @JsonProperty("frmtrm_nm") String previousTermName,
        @JsonProperty("frmtrm_amount") String previousTermAmount,
        @JsonProperty("bfefrmtrm_nm") String beforePreviousTermName,
        @JsonProperty("bfefrmtrm_amount") String beforePreviousTermAmount,
        @JsonProperty("ord") String order,
        String currency) {

    public OpenDartFinancialRow(String receiptNumber, String businessYear, String reportCode,
            String financialStatementDivision, String statementDivision, String accountId,
            String accountName, String currentTerm, String currentTermAmount, String currency) {
        this(receiptNumber, businessYear, reportCode, null, financialStatementDivision,
                statementDivision, null, accountId, accountName, null, currentTerm, null,
                currentTermAmount, null, null, null, null, null, currency);
    }
}
