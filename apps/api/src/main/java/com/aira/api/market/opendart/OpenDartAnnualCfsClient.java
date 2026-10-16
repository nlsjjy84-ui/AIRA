package com.aira.api.market.opendart;

public interface OpenDartAnnualCfsClient {
    OpenDartFinancialResponse fetch(String corpCode, int businessYear);

    /** fsDiv: CFS(연결) 또는 OFS(별도). 연결재무제표가 없는 회사에만 OFS를 요청한다. */
    default OpenDartFinancialResponse fetch(String corpCode, int businessYear, String fsDiv) {
        if ("CFS".equals(fsDiv)) return fetch(corpCode, businessYear);
        throw new UnsupportedOperationException("OFS is not supported by this client");
    }
}
