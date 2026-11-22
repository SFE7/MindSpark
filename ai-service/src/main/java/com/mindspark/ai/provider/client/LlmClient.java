package com.mindspark.ai.provider.client;

import java.time.Duration;

import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;

public interface LlmClient {

    ChatResult complete(ChatRequest request);

    default ChatResult complete(ChatRequest request, Duration readTimeout) {
        return complete(request);
    }
}
