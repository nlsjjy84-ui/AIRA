package com.aira.api.market.krx;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpKrxClient implements KrxClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String key;

    @Autowired
    public HttpKrxClient(ObjectMapper json, @Value("${aira.krx.auth-key:}") String key) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), json, key);
    }
    HttpKrxClient(HttpClient http, ObjectMapper json, String key) {
        this.http = http;
        this.json = json;
        this.key = key == null ? "" : key;
    }
    @Override
    public KrxSnapshot fetch(KrxDataset dataset, LocalDate date) {
        if (dataset == null || date == null) throw new IllegalArgumentException("Explicit KRX dataset and date are required");
        if (key.isBlank()) throw new IllegalStateException("KRX AUTH_KEY is not configured");
        String basDd = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataset.url(basDd)))
                .header("AUTH_KEY", key).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response;
        try { response = http.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KRX request interrupted");
        } catch (IOException failure) { throw new IllegalStateException("KRX request failed"); }
        int status = response.statusCode();
        if (status != 200) throw new IllegalStateException("KRX HTTP status " + status);
        try { return parse(dataset, date, response.body()); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Malformed KRX response", invalid); }
    }
    KrxSnapshot parse(KrxDataset dataset, LocalDate date, String body) {
        Object root = json.readValue(body, Object.class);
        if (!(root instanceof Map<?, ?> object) || object.size() != 1 || !(object.get("OutBlock_1") instanceof List<?> block)) {
            throw new IllegalArgumentException("KRX OutBlock_1 array is required");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Object item : block) {
            if (!(item instanceof Map<?, ?> raw)) throw new IllegalArgumentException("KRX row object is required");
            Map<String, String> row = new LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String field) || !dataset.fields().contains(field)
                        || (entry.getValue() != null && !(entry.getValue() instanceof String))) {
                    throw new IllegalArgumentException("Unexpected KRX row field");
                }
                row.put(field, (String) entry.getValue());
            }
            rows.add(row);
        }
        return KrxSnapshot.validated(dataset, date, rows);
    }
}
