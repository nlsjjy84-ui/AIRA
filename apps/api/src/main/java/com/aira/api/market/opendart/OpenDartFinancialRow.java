package com.aira.api.market.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenDartFinancialRow(
        @JsonProperty("rcept_no") String receiptNumber,
        @JsonProperty("bsns_year") String businessYear,
        @JsonProperty("reprt_code") String reportCode,
        @JsonProperty("fs_div") String financialStatementDivision,
        @JsonProperty("sj_div") String statementDivision,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("account_nm") String accountName,
        @JsonProperty("thstrm_dt") String currentTerm,
        @JsonProperty("thstrm_amount") String currentTermAmount,
        String currency) {
}
