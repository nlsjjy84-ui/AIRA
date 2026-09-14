package com.aira.api.market.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenDartPeriodWitnessResponse(String status, List<Row> list) {
    public record Row(
            @JsonProperty("corp_code") String corpCode,
            @JsonProperty("bsns_year") String businessYear,
            @JsonProperty("reprt_code") String reportCode,
            @JsonProperty("rcept_no") String receiptNumber,
            @JsonProperty("fs_div") String financialStatementDivision,
            @JsonProperty("thstrm_dt") String currentTerm) {}
}
