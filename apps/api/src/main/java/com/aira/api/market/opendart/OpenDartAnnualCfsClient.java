package com.aira.api.market.opendart;

public interface OpenDartAnnualCfsClient {
    OpenDartFinancialResponse fetch(String corpCode, int businessYear);
}
