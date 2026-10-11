package com.aira.api.personalfinance.ai;

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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Optional Google Gemini (generateContent) adapter. Only active when aira.ai.gemini.enabled=true.
 * It reuses the same aggregate-only prompt as the OpenAI adapter and never sends
 * user/account/card/transaction identifiers. If it fails, the caller keeps the RULE analysis.
 */
@Component
@Primary
@ConditionalOnProperty(name = "aira.ai.gemini.enabled", havingValue = "true")
public class GeminiPersonalFinanceAiProvider implements PersonalFinanceAiProvider {
    private final boolean enabled;
    private final String apiKey;
    private final URI endpoint;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json;

    @Autowired
    public GeminiPersonalFinanceAiProvider(
            @Value("${aira.ai.gemini.enabled:false}") boolean enabled,
            @Value("${aira.ai.gemini.api-key:}") String apiKey,
            @Value("${aira.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
            @Value("${aira.ai.gemini.model:gemini-3.5-flash-lite}") String model,
            ObjectMapper json) {
        this(enabled, apiKey, URI.create(trimSlash(baseUrl) + "/" + (model == null ? "" : model.trim()) + ":generateContent"),
                model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json);
    }

    GeminiPersonalFinanceAiProvider(boolean enabled, String apiKey, URI endpoint,
            String model, HttpClient http, ObjectMapper json) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.model = model == null ? "" : model.trim();
        this.http = http;
        this.json = json;
    }

    private static String trimSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    @Override
    public boolean available() {
        return enabled && !apiKey.isBlank() && !model.isBlank();
    }

    @Override public String providerKey() { return "GEMINI_GENERATE_CONTENT"; }
    @Override public String modelKey() { return model; }

    @Override
    public AiProviderResult explain(AiExplanationInput input) {
        if (!available()) throw new AiProviderException("PROVIDER_DISABLED");
        try {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("text", OpenAiPersonalFinanceAiProvider.prompt(input));
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
                throw new AiProviderException("HTTP_" + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (AiProviderException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("INTERRUPTED");
        } catch (Exception failure) {
            throw new AiProviderException("PROVIDER_FAILURE");
        }
    }

    private AiProviderResult parseResponse(String body) throws Exception {
        JsonNode root = json.readTree(body);
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            text.append(part.path("text").asText(""));
        }
        String answer = text.toString().trim();
        if (answer.isBlank()) throw new AiProviderException("EMPTY_OUTPUT");
        if (!finish.isBlank() && !"STOP".equals(finish)) throw new AiProviderException("INCOMPLETE_RESPONSE");
        long inputTokens = root.path("usageMetadata").path("promptTokenCount").asLong(0);
        long outputTokens = root.path("usageMetadata").path("candidatesTokenCount").asLong(0);
        return new AiProviderResult(answer, inputTokens, outputTokens);
    }
}
