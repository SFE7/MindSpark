package com.mindspark.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai.caps")
public class AiCapsProperties {

    private int maxTopicChars = 500;
    private int maxIdeaChars = 300;
    private int maxPayloadChars = 6000;
    private int maxOutputTokens = 800;

    public static String trimTo(String input, int maxLen) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
