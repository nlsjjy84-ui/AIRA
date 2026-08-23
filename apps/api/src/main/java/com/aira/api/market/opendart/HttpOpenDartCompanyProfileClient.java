package com.aira.api.market.opendart;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpOpenDartCompanyProfileClient implements OpenDartCompanyProfileClient {
    static final String ENDPOINT = "https://opendart.fss.or.kr/api/company.json";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public HttpOpenDartCompanyProfileClient(ObjectMapper objectMapper,
            @Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                objectMapper, apiKey);
    }

    HttpOpenDartCompanyProfileClient(HttpClient httpClient, ObjectMapper objectMapper,
            String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public OpenDartCompanyProfile fetch(String corpCode) {
        if (apiKey.isBlank()) throw provider("OpenDART API key is not configured");
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        URI uri = URI.create(ENDPOINT + "?crtfc_key=" + encode(apiKey)
                + "&corp_code=" + encode(corpCode));
        HttpResponse<String> response;
        try {
            response = httpClient.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw provider("OpenDART company profile request was interrupted");
        } catch (IOException exception) {
            throw provider("OpenDART company profile request failed");
        }
        if (response.statusCode() != 200) throw provider("OpenDART company profile returned HTTP " + response.statusCode());
        try {
            JsonNode body = objectMapper.readTree(response.body());
            if (!"000".equals(text(body, "status"))) throw provider("OpenDART company profile failed with status " + text(body, "status"));
            String accMt = text(body, "acc_mt");
            if (accMt == null || !accMt.matches("(0[1-9]|1[0-2])")) throw provider("OpenDART company profile acc_mt is invalid");
            return new OpenDartCompanyProfile(required(text(body, "corp_code"), "corp_code"),
                    required(text(body, "corp_name"), "corp_name"), Integer.parseInt(accMt));
        } catch (OpenDartProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw provider("OpenDART company profile response could not be read");
        }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw provider("OpenDART company profile " + name + " is missing");
        return value.trim();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static OpenDartProviderException provider(String message) {
        return new OpenDartProviderException(OpenDartProviderException.Category.PROVIDER_FAILURE, message);
    }
}
