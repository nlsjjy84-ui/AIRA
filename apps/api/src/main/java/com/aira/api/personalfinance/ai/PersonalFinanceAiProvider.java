package com.aira.api.personalfinance.ai;

public interface PersonalFinanceAiProvider {
    boolean available();
    String providerKey();
    String modelKey();
    AiProviderResult explain(AiExplanationInput input);
}
