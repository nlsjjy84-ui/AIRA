package com.aira.api.assist;

import java.util.List;

/** method is AI only when the model text passed every guard; otherwise RULE with a deterministic text. */
public record AssistResponse(String method, String text, String providerKey, String modelKey,
        List<String> limitations) {}
