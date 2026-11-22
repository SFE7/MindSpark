package com.mindspark.ai.provider.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.provider.exception.ModelUnavailable;
import com.mindspark.ai.provider.exception.ProviderAuthError;
import com.mindspark.ai.provider.exception.ProviderBadRequest;
import com.mindspark.ai.provider.exception.ProviderRateLimited;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    private static final String API_KEY = "test-mock-secret-key-12345";
    private static final String MODEL = "openai/gpt-oss-120b";

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Successful chat completion parses text, returned model, and token usage")
    void testSuccessfulChatCompletion() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        String responseJson = """
                {
                  "id": "chatcmpl-123",
                  "model": "openai/gpt-oss-120b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"ideas\\": [\\"Idea 1\\"]}"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 15,
                    "completion_tokens": 25,
                    "total_tokens": 40
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("System test"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("User test"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder()
                .systemPrompt("System test")
                .userPrompt("User test")
                .maxOutputTokens(800)
                .temperature(0.7)
                .wantJson(true)
                .build();

        ChatResult result = client.complete(request);

        assertNotNull(result);
        assertEquals("{\"ideas\": [\"Idea 1\"]}", result.text());
        assertEquals("openai/gpt-oss-120b", result.modelUsed());
        assertNotNull(result.tokenUsage());
        assertEquals(15, result.tokenUsage().promptTokens());
        assertEquals(25, result.tokenUsage().completionTokens());
        assertEquals(40, result.tokenUsage().totalTokens());
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 maps to ProviderRateLimited and parses Retry-After header")
    void testRateLimitExceptionWithRetryAfter() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "45")
                        .body("{\"error\": {\"message\": \"Rate limit reached\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ProviderRateLimited ex = assertThrows(ProviderRateLimited.class, () -> client.complete(request));
        assertEquals(45L, ex.getRetryAfterSeconds());
        assertFalse(ex.getMessage().contains(API_KEY), "API key must not appear in exception message");
        assertFalse(ex.getMessage().contains("Rate limit reached"), "Provider error body must not leak into exception message");
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 404 maps to ModelUnavailable without leaking provider response")
    void testModelNotFoundMapsToModelUnavailable() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\": {\"message\": \"The model `openai/gpt-oss-120b` does not exist\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ModelUnavailable ex = assertThrows(ModelUnavailable.class, () -> client.complete(request));
        assertFalse(ex.getMessage().contains(API_KEY), "API key must not leak");
        assertFalse(ex.getMessage().contains("The model `openai/gpt-oss-120b` does not exist"), "Provider body must not leak");
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 400 with model_not_found error code maps to ModelUnavailable")
    void testHttp400WithModelNotFoundMapsToModelUnavailable() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": {\"code\": \"model_not_found\", \"message\": \"The model does not exist\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ModelUnavailable ex = assertThrows(ModelUnavailable.class, () -> client.complete(request));
        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(API_KEY));
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 400 with json_validate_failed or bad parameters maps to ProviderBadRequest")
    void testHttp400WithJsonValidateFailedMapsToProviderBadRequest() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": {\"code\": \"json_validate_failed\", \"message\": \"Failed to validate JSON.\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ProviderBadRequest ex = assertThrows(ProviderBadRequest.class, () -> client.complete(request));
        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(API_KEY));
        mockServer.verify();
    }

    @Test
    @DisplayName("Reasoning effort and reasoning format are injected into request payload for Groq")
    void testReasoningEffortAndFormatConfiguredInPayload() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                "low",
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.reasoning_format").value("parsed"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();
        ChatResult result = client.complete(request);

        assertNotNull(result);
        assertEquals("ok", result.text());
        assertEquals("stop", result.finishReason());
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 401/403 maps to ProviderAuthError and never leaks API key")
    void testAuthErrorNeverLeaksApiKey() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\": {\"message\": \"Invalid API Key provided: " + API_KEY + "\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ProviderAuthError ex = assertThrows(ProviderAuthError.class, () -> client.complete(request));
        assertFalse(ex.getMessage().contains(API_KEY), "API key must never appear in exception message");
        assertFalse(ex.getMessage().contains("Invalid API Key provided"), "Provider body must not leak");
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 500/503 maps to ProviderUnavailable")
    void testServerErrorMapsToProviderUnavailable() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                restClientBuilder.build(),
                BASE_URL,
                API_KEY,
                MODEL,
                objectMapper
        );

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\": {\"message\": \"Service temporarily overloaded\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        ChatRequest request = ChatRequest.builder().userPrompt("Hello").build();

        ProviderUnavailable ex = assertThrows(ProviderUnavailable.class, () -> client.complete(request));
        assertFalse(ex.getMessage().contains(API_KEY));
        assertFalse(ex.getMessage().contains("Service temporarily overloaded"));
        mockServer.verify();
    }
}
