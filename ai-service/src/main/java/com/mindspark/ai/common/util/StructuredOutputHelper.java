package com.mindspark.ai.common.util;

import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StructuredOutputHelper {

    private final ObjectMapper objectMapper;

    public StructuredOutputHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T parseAndValidate(String rawText, Class<T> targetClass, Consumer<T> customValidator) {
        if (rawText == null || rawText.isBlank()) {
            throw new InvalidModelOutputException("Model returned empty or null content");
        }

        String cleaned = cleanJsonFences(rawText);

        T result;
        try {
            result = objectMapper.readValue(cleaned, targetClass);
        } catch (Exception e) {
            log.warn("Failed to parse model output as JSON for class {}: {}", targetClass.getSimpleName(), e.getMessage());
            throw new InvalidModelOutputException("Unparseable model JSON for " + targetClass.getSimpleName(), e);
        }

        if (result == null) {
            throw new InvalidModelOutputException("Model parsed output is null for class " + targetClass.getSimpleName());
        }

        if (customValidator != null) {
            try {
                customValidator.accept(result);
            } catch (InvalidModelOutputException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Custom validation failed for model output {}: {}", targetClass.getSimpleName(), e.getMessage());
                throw new InvalidModelOutputException("Model output failed validation: " + e.getMessage(), e);
            }
        }

        return result;
    }

    public static String cleanJsonFences(String rawText) {
        if (rawText == null) {
            return "";
        }
        String trimmed = rawText.trim();

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        int firstBracket = trimmed.indexOf('[');
        int lastBracket = trimmed.lastIndexOf(']');

        if (firstBrace != -1 && lastBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        } else if (firstBracket != -1 && lastBracket != -1) {
            return trimmed.substring(firstBracket, lastBracket + 1);
        }

        return trimmed;
    }
}
