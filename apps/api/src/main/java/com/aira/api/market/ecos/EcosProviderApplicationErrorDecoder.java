package com.aira.api.market.ecos;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class EcosProviderApplicationErrorDecoder {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, EcosProviderException.Category> CATEGORIES = Map.ofEntries(
            Map.entry("INFO-100", EcosProviderException.Category.AUTHENTICATION),
            Map.entry("INFO-200", EcosProviderException.Category.NO_DATA),
            Map.entry("ERROR-100", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-101", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-200", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-300", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-301", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-400", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-500", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-600", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-601", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-602", EcosProviderException.Category.RATE_LIMIT));

    private EcosProviderApplicationErrorDecoder() {
    }

    static void throwIfPresent(EcosRawResponse raw) {
        JsonNode root = readTree(raw.body());
        if (root == null || !root.isObject()) return;

        JsonNode result = root.get("RESULT");
        if (result == null) return;
        if (root.size() != 1 || !result.isObject() || result.size() != 2) {
            throw malformed("ECOS RESULT envelope schema is invalid");
        }

        String code = requiredText(result, "CODE");
        requiredText(result, "MESSAGE");
        EcosProviderException.Category category = CATEGORIES.get(code);
        if (category == null) {
            throw malformed("ECOS provider returned an unrecognized application error code");
        }
        throw new EcosProviderException(category, "ECOS provider returned " + code, code);
    }
    private static JsonNode readTree(String body) {
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw malformed("ECOS RESULT " + field + " must be text");
        }
        String text = value.stringValue();
        if (text.isBlank() || !text.equals(text.strip())) {
            throw malformed("ECOS RESULT " + field + " must be non-blank text");
        }
        return text;
    }

    private static EcosProviderException malformed(String message) {
        return new EcosProviderException(
                EcosProviderException.Category.MALFORMED_RESPONSE, message);
    }
}
