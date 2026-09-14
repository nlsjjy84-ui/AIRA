package com.aira.api.market.ecos;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class EcosStatisticSearchRequestBuilder {
    private EcosStatisticSearchRequestBuilder() {
    }

    static URI build(URI baseEndpoint, String apiKey, EcosRealGdpSearchRequest request) {
        if (baseEndpoint == null) throw new IllegalArgumentException("baseEndpoint must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new EcosProviderException(EcosProviderException.Category.AUTHENTICATION,
                    "ECOS API key is not configured");
        }
        if (request == null) throw new IllegalArgumentException("request must not be null");

        try {
            String path = "StatisticSearch/" + encode(apiKey) + "/json/kr/"
                    + request.startRow() + "/" + request.endRow() + "/"
                    + EcosRealGdpContract.STAT_CODE + "/" + EcosRealGdpContract.CYCLE + "/"
                    + request.startTime() + "/" + request.endTime() + "/"
                    + EcosRealGdpContract.ITEM_CODE1 + "/?/?/?";
            return baseEndpoint.resolve(path);
        } catch (RuntimeException exception) {
            throw new EcosProviderException(EcosProviderException.Category.INVALID_REQUEST,
                    "ECOS StatisticSearch request could not be constructed");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
