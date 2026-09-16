package com.aira.api.personalfinance.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Optional OpenAI Responses API adapter. It is disabled by default and never sends
 * user/account/card/transaction identifiers; only aggregate spending changes are serialized.
 */
@Component
public class OpenAiPersonalFinanceAiProvider implements PersonalFinanceAiProvider {
    private final boolean enabled;
    private final String apiKey;
    private final URI endpoint;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json;

    @Autowired
    public OpenAiPersonalFinanceAiProvider(
            @Value("${aira.ai.openai.enabled:false}") boolean enabled,
            @Value("${aira.ai.openai.api-key:}") String apiKey,
            @Value("${aira.ai.openai.endpoint:https://api.openai.com/v1/responses}") String endpoint,
            @Value("${aira.ai.openai.model:gpt-5.6-luna}") String model,
            ObjectMapper json) {
        this(enabled, apiKey, URI.create(endpoint), model,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json);
    }

    OpenAiPersonalFinanceAiProvider(boolean enabled, String apiKey, URI endpoint,
            String model, HttpClient http, ObjectMapper json) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.model = model == null ? "" : model.trim();
        this.http = http;
        this.json = json;
    }

    @Override
    public boolean available() {
        return enabled && !apiKey.isBlank() && !model.isBlank();
    }

    @Override public String providerKey() { return "OPENAI_RESPONSES"; }
    @Override public String modelKey() { return model; }

    @Override
    public AiProviderResult explain(AiExplanationInput input) {
        if (!available()) throw new AiProviderException("PROVIDER_DISABLED");
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("store", false);
            body.put("max_output_tokens", 220);
            body.put("input", prompt(input));

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
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
        if (!"completed".equals(root.path("status").asText())) {
            throw new AiProviderException("INCOMPLETE_RESPONSE");
        }
        String text = "";
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    text = content.path("text").asText("").trim();
                    if (!text.isBlank()) break;
                }
            }
            if (!text.isBlank()) break;
        }
        if (text.isBlank()) throw new AiProviderException("EMPTY_OUTPUT");
        long inputTokens = root.path("usage").path("input_tokens").asLong(0);
        long outputTokens = root.path("usage").path("output_tokens").asLong(0);
        return new AiProviderResult(text, inputTokens, outputTokens);
    }

    private static String prompt(AiExplanationInput input) {
        StringBuilder out = new StringBuilder();
        out.append("You explain a user's spending changes factually in Korean. ")
                .append("Do not recommend spending, investments, products, budgets, or actions. ")
                .append("Do not infer motives. State uncertainty when data is insufficient. ")
                .append("Use only the aggregates below; they contain no account or transaction identifiers.\n")
                .append("month=").append(input.month())
                .append(", previousMonth=").append(input.previousMonth())
                .append(", currency=").append(input.currencyCode())
                .append(", totalCurrent=").append(input.totalCurrent())
                .append(", totalPrevious=").append(input.totalPrevious())
                .append(", totalDelta=").append(input.totalDelta()).append('\n');
        input.categories().forEach(category -> out.append("category=").append(category.category())
                .append(", current=").append(category.current())
                .append(", previous=").append(category.previous())
                .append(", delta=").append(category.delta()).append('\n'));
        out.append("Return 2-4 concise Korean sentences. Distinguish observed change from unknown cause.");
        return out.toString();
    }
}
