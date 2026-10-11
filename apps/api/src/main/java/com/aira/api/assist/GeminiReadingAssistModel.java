package com.aira.api.assist;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Gemini generateContent adapter for reading assistance. Active only when aira.ai.gemini.enabled=true. */
@Component
@ConditionalOnProperty(name = "aira.ai.gemini.enabled", havingValue = "true")
public class GeminiReadingAssistModel implements ReadingAssistModel {
    private final boolean enabled;
    private final String apiKey;
    private final URI endpoint;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json;

    @Autowired
    public GeminiReadingAssistModel(
            @Value("${aira.ai.gemini.enabled:false}") boolean enabled,
            @Value("${aira.ai.gemini.api-key:}") String apiKey,
            @Value("${aira.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
            @Value("${aira.ai.gemini.model:gemini-3.5-flash-lite}") String model,
            ObjectMapper json) {
        this(enabled, apiKey,
                URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                        + "/" + model.trim() + ":generateContent"),
                model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json);
    }

    GeminiReadingAssistModel(boolean enabled, String apiKey, URI endpoint, String model,
            HttpClient http, ObjectMapper json) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.model = model == null ? "" : model.trim();
        this.http = http;
        this.json = json;
    }

    @Override public boolean available() { return enabled && !apiKey.isBlank() && !model.isBlank(); }
    @Override public String providerKey() { return "GEMINI_GENERATE_CONTENT"; }
    @Override public String modelKey() { return model; }

    @Override
    public String complete(String prompt) {
        if (!available()) throw new IllegalStateException("PROVIDER_DISABLED");
        try {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("text", prompt);
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", "user");
            content.put("parts", List.of(part));
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxOutputTokens", 2048);
            config.put("temperature", 0.2);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(content));
            body.put("generationConfig", config);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(25))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP_" + response.statusCode());
            }
            JsonNode candidate = json.readTree(response.body()).path("candidates").path(0);
            String finish = candidate.path("finishReason").asText("");
            StringBuilder text = new StringBuilder();
            for (JsonNode p : candidate.path("content").path("parts")) {
                if (p.path("thought").asBoolean(false)) continue;
                text.append(p.path("text").asText(""));
            }
            if (!finish.isBlank() && !"STOP".equals(finish)) throw new IllegalStateException("INCOMPLETE_RESPONSE");
            String answer = text.toString().trim();
            if (answer.isBlank()) throw new IllegalStateException("EMPTY_OUTPUT");
            return answer;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("INTERRUPTED");
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("PROVIDER_FAILURE");
        }
    }
}
