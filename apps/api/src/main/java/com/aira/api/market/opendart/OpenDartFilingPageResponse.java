package com.aira.api.market.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw OpenDART list.json page response. */
public record OpenDartFilingPageResponse(
        String status,
        String message,
        @JsonProperty("page_no") Integer pageNumber,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("total_page") Integer totalPages,
        List<OpenDartFilingTransportRow> list) {
}
