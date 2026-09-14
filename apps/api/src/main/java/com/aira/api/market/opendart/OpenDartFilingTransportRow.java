package com.aira.api.market.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw list.json row. Validation and canonical typing happen after transport parsing. */
public record OpenDartFilingTransportRow(
        @JsonProperty("corp_cls") String corpClass,
        @JsonProperty("corp_code") String corpCode,
        @JsonProperty("corp_name") String corpName,
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("report_nm") String reportName,
        @JsonProperty("rcept_no") String receiptNumber,
        @JsonProperty("flr_nm") String filerName,
        @JsonProperty("rcept_dt") String receiptDate,
        String rm) {
}
