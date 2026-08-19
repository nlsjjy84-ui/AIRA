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

@Component
public final class HttpOpenDartCompanyDirectoryClient implements OpenDartCompanyDirectoryClient {
    static final String ENDPOINT = "https://opendart.fss.or.kr/api/corpCode.xml";

    private final HttpClient httpClient;
    private final OpenDartCompanyDirectoryParser parser;
    private final String apiKey;

    @Autowired
    public HttpOpenDartCompanyDirectoryClient(@Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new OpenDartCompanyDirectoryParser(), apiKey);
    }

    HttpOpenDartCompanyDirectoryClient(HttpClient httpClient,
            OpenDartCompanyDirectoryParser parser, String apiKey) {
        this.httpClient = httpClient;
        this.parser = parser;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public OpenDartCompanyDirectory fetch() {
        if (apiKey.isBlank()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.AUTHENTICATION,
                    "OpenDART API key is not configured");
        }
        URI uri = URI.create(ENDPOINT + "?crtfc_key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART directory request was interrupted");
        } catch (IOException exception) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.TRANSPORT,
                    "OpenDART directory request failed");
        }
        if (response.statusCode() != 200) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.PROVIDER_FAILURE,
                    "OpenDART directory returned HTTP status " + response.statusCode());
        }
        return parser.parse(response.body());
    }
}
