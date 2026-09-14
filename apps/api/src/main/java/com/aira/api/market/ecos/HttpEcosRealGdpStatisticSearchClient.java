package com.aira.api.market.ecos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpEcosRealGdpStatisticSearchClient
        implements EcosRealGdpStatisticSearchClient {
    static final URI BASE_ENDPOINT = URI.create("https://ecos.bok.or.kr/api/");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @FunctionalInterface
    interface Sender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final Sender sender;
    private final String apiKey;
    private final URI baseEndpoint;
    private final EcosRealGdpStatisticSearchParser parser;
    private final EcosRequestExecutionGuard executionGuard;

    @Autowired
    public HttpEcosRealGdpStatisticSearchClient(
            ObjectMapper objectMapper,
            @Value("${aira.ecos.api-key:}") String apiKey,
            EcosRequestExecutionGuard executionGuard) {
        this(defaultSender(), objectMapper, apiKey, BASE_ENDPOINT, executionGuard);
    }

    HttpEcosRealGdpStatisticSearchClient(
            Sender sender, ObjectMapper objectMapper, String apiKey, URI baseEndpoint) {
        this(sender, objectMapper, apiKey, baseEndpoint,
                new EcosRequestExecutionGuard(System::nanoTime, Thread::sleep, Duration.ZERO));
    }

    HttpEcosRealGdpStatisticSearchClient(
            Sender sender, ObjectMapper objectMapper, String apiKey, URI baseEndpoint,
            EcosRequestExecutionGuard executionGuard) {
        if (sender == null) throw new IllegalArgumentException("sender must not be null");
        if (objectMapper == null) throw new IllegalArgumentException("objectMapper must not be null");
        if (baseEndpoint == null) throw new IllegalArgumentException("baseEndpoint must not be null");
        this.sender = sender;
        this.apiKey = apiKey == null ? "" : apiKey;
        if (executionGuard == null) throw new IllegalArgumentException("executionGuard must not be null");
        this.baseEndpoint = baseEndpoint;
        this.parser = new EcosRealGdpStatisticSearchParser(objectMapper);
        this.executionGuard = executionGuard;
    }

    private static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return request -> client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public EcosStatisticSearchPage fetch(EcosRealGdpSearchRequest request) {
        URI uri = EcosStatisticSearchRequestBuilder.build(baseEndpoint, apiKey, request);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = executionGuard.execute(
                () -> sender.send(httpRequest), "StatisticSearch");
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new EcosProviderException(EcosProviderException.Category.MALFORMED_RESPONSE,
                    "ECOS StatisticSearch returned an empty response body");
        }
        if (body.contains(apiKey) || body.contains(encodedKey())) {
            throw new EcosProviderException(EcosProviderException.Category.MALFORMED_RESPONSE,
                    "ECOS StatisticSearch response contained credential material");
        }
        EcosRawResponse raw = new EcosRawResponse(response.statusCode(), body);
        EcosProviderApplicationErrorDecoder.throwIfPresent(raw);
        return parser.parse(raw);
    }

    private String encodedKey() {
        return java.net.URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
