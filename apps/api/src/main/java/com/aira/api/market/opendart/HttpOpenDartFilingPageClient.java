package com.aira.api.market.opendart;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** OpenDART list.json transport adapter. Canonical selection and persistence are intentionally outside this class. */
@Component
public final class HttpOpenDartFilingPageClient implements OpenDartFilingPageClient {
    static final String ENDPOINT = "https://opendart.fss.or.kr/api/list.json";
    static final int PAGE_COUNT = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public HttpOpenDartFilingPageClient(ObjectMapper objectMapper,
            @Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper, apiKey);
    }

    HttpOpenDartFilingPageClient(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public OpenDartFilingPageResponse fetch(OpenDartFilingDiscoveryRequest request, int pageNumber) {
        if (request == null) {
            throw new IllegalArgumentException("OpenDART filing discovery request is required");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("OpenDART page number must be positive");
        }
        if (apiKey.isBlank()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.AUTHENTICATION,
                    "OpenDART API key is not configured");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(buildUri(request, pageNumber))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART filing discovery request was interrupted");
        } catch (IOException exception) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART filing discovery request failed");
        }
        if (response.statusCode() != 200) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.PROVIDER_FAILURE,
                    "OpenDART filing discovery returned HTTP status " + response.statusCode());
        }
        try {
            return objectMapper.readValue(response.body(), OpenDartFilingPageResponse.class);
        } catch (RuntimeException exception) {
            // Raw provider payload는 key/예상 밖 민감값을 포함할 수 있어 오류 메시지에 반영하지 않는다.
            throw new OpenDartProviderException(OpenDartProviderException.Category.MALFORMED_RESPONSE,
                    "OpenDART filing discovery response could not be read");
        }
    }

    URI buildUri(OpenDartFilingDiscoveryRequest request, int pageNumber) {
        StringBuilder query = new StringBuilder();
        add(query, "crtfc_key", apiKey);
        add(query, "corp_code", request.corpCode());
        add(query, "last_reprt_at", "N");
        add(query, "sort", "date");
        add(query, "sort_mth", "asc");
        add(query, "page_no", Integer.toString(pageNumber));
        add(query, "page_count", Integer.toString(PAGE_COUNT));
        if (request.beginningDate() != null) {
            add(query, "bgn_de", DateTimeFormatter.BASIC_ISO_DATE.format(request.beginningDate()));
        }
        if (request.endingDate() != null) {
            add(query, "end_de", DateTimeFormatter.BASIC_ISO_DATE.format(request.endingDate()));
        }
        if (request.disclosureType() != null) {
            add(query, "pblntf_ty", request.disclosureType());
        }
        if (request.disclosureDetailType() != null) {
            add(query, "pblntf_detail_ty", request.disclosureDetailType());
        }
        return URI.create(ENDPOINT + "?" + query);
    }

    private static void add(StringBuilder query, String key, String value) {
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(encode(key)).append('=').append(encode(value));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
