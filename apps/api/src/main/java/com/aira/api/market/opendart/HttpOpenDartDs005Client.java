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

@Component
public final class HttpOpenDartDs005Client implements OpenDartDs005Client {
    private final HttpClient http;
    private final String apiKey;
    @Autowired
    public HttpOpenDartDs005Client(@Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }
    HttpOpenDartDs005Client(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey;
    }
    @Override public String fetch(OpenDartDs005Request request) {
        if (request == null) throw new IllegalArgumentException("DS005 request is required");
        if (apiKey.isBlank()) throw new OpenDartProviderException(OpenDartProviderException.Category.AUTHENTICATION,
                "OpenDART API key is not configured");
        String url = "https://opendart.fss.or.kr/api/" + request.endpointKey() + ".json?crtfc_key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) + "&corp_code=" + request.corpCode()
                + "&bgn_de=" + DateTimeFormatter.BASIC_ISO_DATE.format(request.beginningDate())
                + "&end_de=" + DateTimeFormatter.BASIC_ISO_DATE.format(request.endingDate());
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new OpenDartProviderException(
                    OpenDartProviderException.Category.PROVIDER_FAILURE, "OpenDART DS005 HTTP status " + response.statusCode());
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT, "OpenDART DS005 request interrupted");
        } catch (IOException exception) {
            // The credential-bearing URI and exception detail must not escape transport.
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT, "OpenDART DS005 request failed");
        }
    }
}
