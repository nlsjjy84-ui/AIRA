package com.aira.api.market.ecos;

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
public final class HttpEcosMetadataClient implements EcosMetadataClient {
    static final URI BASE_ENDPOINT = URI.create("https://ecos.bok.or.kr/api/");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @FunctionalInterface
    interface Sender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final Sender sender;
    private final String apiKey;
    private final URI baseEndpoint;
    private final EcosRequestExecutionGuard executionGuard;

    @Autowired
    public HttpEcosMetadataClient(
            @Value("${aira.ecos.api-key:}") String apiKey,
            EcosRequestExecutionGuard executionGuard) {
        this(defaultSender(), apiKey, BASE_ENDPOINT, executionGuard);
    }

    HttpEcosMetadataClient(Sender sender, String apiKey, URI baseEndpoint) {
        this(sender, apiKey, baseEndpoint,
                new EcosRequestExecutionGuard(System::nanoTime, Thread::sleep, Duration.ZERO));
    }

    HttpEcosMetadataClient(
            Sender sender, String apiKey, URI baseEndpoint,
            EcosRequestExecutionGuard executionGuard) {
        if (sender == null) throw new IllegalArgumentException("sender must not be null");
        if (baseEndpoint == null) throw new IllegalArgumentException("baseEndpoint must not be null");
        this.sender = sender;
        if (executionGuard == null) throw new IllegalArgumentException("executionGuard must not be null");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseEndpoint = baseEndpoint;
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
    public EcosRawResponse fetch(EcosRequestDescriptor request) {
        if (apiKey.isBlank()) {
            throw new EcosProviderException(EcosProviderException.Category.AUTHENTICATION,
                    "ECOS API key is not configured");
        }
        if (request == null) throw new IllegalArgumentException("request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(buildUri(request))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = executionGuard.execute(
                () -> sender.send(httpRequest), "StatisticItemList");
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new EcosProviderException(EcosProviderException.Category.MALFORMED_RESPONSE,
                    "ECOS StatisticItemList returned an empty response body");
        }
        if (body.contains(apiKey) || body.contains(encodeSegment(apiKey))) {
            throw new EcosProviderException(EcosProviderException.Category.MALFORMED_RESPONSE,
                    "ECOS StatisticItemList response contained credential material");
        }
        EcosRawResponse raw = new EcosRawResponse(response.statusCode(), body);
        EcosProviderApplicationErrorDecoder.throwIfPresent(raw);
        return raw;
    }

    URI buildUri(EcosRequestDescriptor request) {
        try {
            String path = EcosRequestDescriptor.OPERATION + "/"
                    + encodeSegment(apiKey) + "/json/kr/"
                    + request.startRow() + "/" + request.endRow() + "/"
                    + encodeSegment(request.statCode()) + "/";
            return baseEndpoint.resolve(path);
        } catch (RuntimeException exception) {
            throw new EcosProviderException(EcosProviderException.Category.INVALID_REQUEST,
                    "ECOS StatisticItemList request could not be constructed");
        }
    }

    private static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
