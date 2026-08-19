package com.aira.api.market.opendart;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HttpOpenDartAnnualCfsClient implements OpenDartAnnualCfsClient {
    static final String ENDPOINT = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json";
    static final String ANNUAL_REPORT_CODE = "11011";
    static final String CFS = "CFS";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public HttpOpenDartAnnualCfsClient(ObjectMapper objectMapper,
            @Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                objectMapper, apiKey);
    }

    HttpOpenDartAnnualCfsClient(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public OpenDartFinancialResponse fetch(String corpCode, int businessYear) {
        if (apiKey.isBlank()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.AUTHENTICATION,
                    "OpenDART API key is not configured");
        }
        var uri = URI.create(ENDPOINT + "?crtfc_key=" + encode(apiKey)
                + "&corp_code=" + encode(corpCode)
                + "&bsns_year=" + businessYear
                + "&reprt_code=" + ANNUAL_REPORT_CODE
                + "&fs_div=" + CFS);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART request was interrupted");
        } catch (IOException exception) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART request failed");
        }
        if (response.statusCode() != 200) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.PROVIDER_FAILURE,
                    "OpenDART returned HTTP status " + response.statusCode());
        }
        try {
            return objectMapper.readValue(response.body(), OpenDartFinancialResponse.class);
        } catch (RuntimeException exception) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.MALFORMED_RESPONSE,
                    "OpenDART response could not be read");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
