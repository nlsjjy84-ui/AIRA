package com.aira.api.market.news;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpGdeltMarketNewsClient implements MarketNewsClient {
    private static final DateTimeFormatter SEEN_AT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");
    private static final String QUERY = "(KOSPI OR KOSDAQ) sourcecountry:southkorea";

    @FunctionalInterface
    interface Sender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final Sender sender;
    private final ObjectMapper json;
    private final URI endpoint;

    @Autowired
    public HttpGdeltMarketNewsClient(ObjectMapper json,
            @Value("${aira.news.gdelt.endpoint:https://api.gdeltproject.org/api/v2/doc/doc}") String endpoint) {
        this(defaultSender(), json, URI.create(endpoint));
    }

    HttpGdeltMarketNewsClient(Sender sender, ObjectMapper json, URI endpoint) {
        if (sender == null || json == null || endpoint == null) throw new IllegalArgumentException("GDELT client dependencies are required");
        this.sender = sender;
        this.json = json;
        this.endpoint = endpoint;
    }

    private static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        return request -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public List<MarketNewsArticle> fetchRecentKoreanMarketNews() {
        HttpRequest request = buildRequest();
        HttpResponse<String> response;
        try { response = sender.send(request); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GDELT request interrupted");
        } catch (IOException failure) {
            throw new IllegalStateException("GDELT request failed: " + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : " " + failure.getMessage()), failure);
        }
        if (response.statusCode() != 200) throw new IllegalStateException("GDELT HTTP status " + response.statusCode());
        return parse(response.body());
    }

    HttpRequest buildRequest() {
        return HttpRequest.newBuilder(buildUri()).timeout(Duration.ofSeconds(20)).GET().build();
    }

    URI buildUri() {
        String query = "query=" + encode(QUERY)
                + "&mode=artlist&maxrecords=20&timespan=24h&sort=datedesc&format=json";
        return URI.create(endpoint.toString() + (endpoint.toString().contains("?") ? "&" : "?") + query);
    }

    List<MarketNewsArticle> parse(String body) {
        if (body == null || body.isBlank()) throw new IllegalArgumentException("GDELT response body is required");
        Object root = json.readValue(body, Object.class);
        if (!(root instanceof Map<?, ?> object) || !(object.get("articles") instanceof List<?> articles))
            throw new IllegalArgumentException("GDELT articles array is required");

        Map<String, MarketNewsArticle> unique = new LinkedHashMap<>();
        for (Object rawArticle : articles) {
            if (!(rawArticle instanceof Map<?, ?> raw)) continue;
            String title = clean(text(raw.get("title")), 500);
            String url = clean(text(raw.get("url")), 1500);
            OffsetDateTime seenAt = parseSeenAt(text(raw.get("seendate")));
            if (title == null || url == null || seenAt == null || !isHttpUrl(url)) continue;
            URI uri = URI.create(url);
            String domain = clean(text(raw.get("domain")), 255);
            if (domain == null) domain = uri.getHost();
            if (domain == null || domain.isBlank()) continue;
            MarketNewsArticle article = new MarketNewsArticle(title, url, domain.toLowerCase(), seenAt,
                    clean(text(raw.get("language")), 64), clean(text(raw.get("sourcecountry")), 64));
            unique.putIfAbsent(url, article);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(MarketNewsArticle::seenAt).reversed()
                        .thenComparing(MarketNewsArticle::originalUrl))
                .toList();
    }

    private static OffsetDateTime parseSeenAt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value.trim(), SEEN_AT); }
        catch (DateTimeParseException invalid) { return null; }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static String text(Object value) { return value instanceof String text ? text : null; }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = HtmlUtils.htmlUnescape(value).replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
