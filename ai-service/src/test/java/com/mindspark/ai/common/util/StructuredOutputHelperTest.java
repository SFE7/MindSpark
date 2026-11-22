package com.mindspark.ai.common.util;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredOutputHelperTest {

    private StructuredOutputHelper helper;

    record SampleIdea(String id, String text) {}
    record SampleResult(List<SampleIdea> ideas) {}

    @BeforeEach
    void setUp() {
        helper = new StructuredOutputHelper(new ObjectMapper());
    }

    @Test
    @DisplayName("Fenced JSON with markdown ```json ... ``` is stripped and parsed cleanly")
    void testFencedJsonParsing() {
        String fencedJson = """
                ```json
                {
                  "ideas": [
                    {"id": "idea-1", "text": "First idea"},
                    {"id": "idea-2", "text": "Second idea"}
                  ]
                }
                ```
                """;

        SampleResult result = helper.parseAndValidate(fencedJson, SampleResult.class, null);

        assertNotNull(result);
        assertEquals(2, result.ideas().size());
        assertEquals("idea-1", result.ideas().get(0).id());
        assertEquals("First idea", result.ideas().get(0).text());
    }

    @Test
    @DisplayName("Surrounding explanatory text before/after JSON is stripped and parsed")
    void testSurroundingTextParsing() {
        String rawWithText = """
                Here are the generated brainstorm ideas:
                {
                  "ideas": [
                    {"id": "idea-99", "text": "Clean idea"}
                  ]
                }
                Hope you like them!
                """;

        SampleResult result = helper.parseAndValidate(rawWithText, SampleResult.class, null);

        assertNotNull(result);
        assertEquals(1, result.ideas().size());
        assertEquals("idea-99", result.ideas().get(0).id());
    }

    @Test
    @DisplayName("Garbage unparseable JSON throws InvalidModelOutputException")
    void testGarbageJsonThrowsInvalidModelOutput() {
        String garbage = "Sorry, I cannot provide JSON because I am an AI.";

        assertThrows(InvalidModelOutputException.class, () ->
                helper.parseAndValidate(garbage, SampleResult.class, null)
        );
    }

    @Test
    @DisplayName("Custom validator rejects unknown IDs and throws InvalidModelOutputException")
    void testIdValidationRejectsUnknownIds() {
        String validJson = """
                {
                  "ideas": [
                    {"id": "unknown-id", "text": "Hallucinated idea"}
                  ]
                }
                """;

        Set<String> allowedIds = Set.of("id-1", "id-2");

        InvalidModelOutputException ex = assertThrows(InvalidModelOutputException.class, () ->
                helper.parseAndValidate(validJson, SampleResult.class, res -> {
                    for (SampleIdea idea : res.ideas()) {
                        if (!allowedIds.contains(idea.id())) {
                            throw new IllegalArgumentException("Unknown ID: " + idea.id());
                        }
                    }
                })
        );

        assertNotNull(ex.getMessage());
    }
}
