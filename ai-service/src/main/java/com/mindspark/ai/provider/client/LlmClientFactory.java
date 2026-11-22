package com.mindspark.ai.provider.client;

import com.mindspark.ai.tier.Tier;

public interface LlmClientFactory {
    LlmClient getClient(String model, Tier tier);
}
