package com.aira.api.market.opendart;

/** Transport-only page client. It never chooses a filing or writes canonical state. */
public interface OpenDartFilingPageClient {
    OpenDartFilingPageResponse fetch(OpenDartFilingDiscoveryRequest request, int pageNumber);
}
