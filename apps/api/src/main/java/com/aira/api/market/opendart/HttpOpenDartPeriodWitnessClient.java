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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HttpOpenDartPeriodWitnessClient implements OpenDartPeriodWitnessClient {
    static final String ENDPOINT = "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json";
    static final String ANNUAL_REPORT_CODE = "11011";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public HttpOpenDartPeriodWitnessClient(ObjectMapper objectMapper,
            @Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                objectMapper, apiKey);
    }

    HttpOpenDartPeriodWitnessClient(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public OpenDartPeriodWitnessResponse fetch(String corpCode, int businessYear) {
        new OpenDartAnnualCfsContext(corpCode, businessYear, ANNUAL_REPORT_CODE, "CFS");
        if (apiKey.isBlank()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.AUTHENTICATION,
                    "OpenDART API key is not configured");
        }
        var uri = URI.create(ENDPOINT + "?crtfc_key=" + encode(apiKey)
                + "&corp_code=" + encode(corpCode)
                + "&bsns_year=" + businessYear
                + "&reprt_code=" + ANNUAL_REPORT_CODE);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART request was interrupted");
        } catch (IOException | RuntimeException exception) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART request failed");
        }
        if (response.statusCode() != 200) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.PROVIDER_FAILURE,
                    "OpenDART returned HTTP status " + response.statusCode());
        }
        try {
            return objectMapper.readValue(response.body(), OpenDartPeriodWitnessResponse.class);
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
