package com.mindspark.ai.provider.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.provider.exception.ModelUnavailable;
import com.mindspark.ai.provider.exception.ProviderAuthError;
import com.mindspark.ai.provider.exception.ProviderBadRequest;
import com.mindspark.ai.provider.exception.ProviderException;
import com.mindspark.ai.provider.exception.ProviderRateLimited;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;
import com.mindspark.ai.provider.model.TokenUsage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAiCompatibleClient implements LlmClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final String reasoningEffort;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String apiKey,
            String model,
            int connectTimeoutSeconds,
            int readTimeoutSeconds,
            String reasoningEffort,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.connectTimeoutSeconds = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 5;
        this.readTimeoutSeconds = readTimeoutSeconds > 0 ? readTimeoutSeconds : 45;
        this.reasoningEffort = reasoningEffort != null ? reasoningEffort : "low";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(this.connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(this.readTimeoutSeconds));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    public OpenAiCompatibleClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String apiKey,
            String model,
            int connectTimeoutSeconds,
            int readTimeoutSeconds,
            ObjectMapper objectMapper) {
        this(restClientBuilder, baseUrl, apiKey, model, connectTimeoutSeconds, readTimeoutSeconds, "low", objectMapper);
    }

    public OpenAiCompatibleClient(RestClient restClient, String baseUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this(restClient, baseUrl, apiKey, model, "low", objectMapper);
    }

    public OpenAiCompatibleClient(RestClient restClient, String baseUrl, String apiKey, String model, String reasoningEffort, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.connectTimeoutSeconds = 5;
        this.readTimeoutSeconds = 45;
        this.reasoningEffort = reasoningEffort != null ? reasoningEffort : "low";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public String getModel() {
        return model;
    }

    @Override
    public ChatResult complete(ChatRequest request) {
        return complete(request, Duration.ofSeconds(readTimeoutSeconds));
    }

    @Override
    public ChatResult complete(ChatRequest request, Duration readTimeout) {
        String endpoint = baseUrl.endsWith("/chat/completions")
                ? baseUrl
                : (baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions");

        Map<String, Object> payload = buildPayload(request);

        RestClient clientToUse = this.restClient;
        if (readTimeout != null && !readTimeout.equals(Duration.ofSeconds(readTimeoutSeconds))) {
            SimpleClientHttpRequestFactory customFactory = new SimpleClientHttpRequestFactory();
            customFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
            customFactory.setReadTimeout(readTimeout);
            clientToUse = this.restClient.mutate().requestFactory(customFactory).build();
        }

        try {
            byte[] responseBytes = clientToUse.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (req, resp) -> {
                        String retryAfterHeader = resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                        Long retryAfterSeconds = parseRetryAfter(retryAfterHeader);
                        throw new ProviderRateLimited("Provider rate limit reached for model " + model, retryAfterSeconds);
                    })
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new ModelUnavailable("Model not found: " + model, 404);
                    })
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Provider authentication failed for model {} with HTTP status {}", model, code);
                        throw new ProviderAuthError("Provider authentication error for model " + model, code);
                    })
                    .onStatus(status -> status.value() == 400, (req, resp) -> {
                        String body = "";
                        try {
                            body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
                        } catch (IOException ignored) {}
                        if (body.contains("model_not_found") || body.contains("does not exist") || body.contains("model not found")) {
                            throw new ModelUnavailable("Model unavailable: " + model, 400);
                        }
                        throw new ProviderBadRequest("Provider bad request for model " + model, 400);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        throw new ProviderUnavailable("Provider server error (" + code + ") for model " + model, code);
                    })
                    .body(byte[].class);

            if (responseBytes == null || responseBytes.length == 0) {
                throw new ProviderUnavailable("Empty response received from model " + model);
            }

            JsonNode root = objectMapper.readTree(responseBytes);
            return parseChatResult(root);

        } catch (ProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ProviderUnavailable("Provider connection or timeout failure for model " + model, e);
        } catch (RestClientException e) {
            throw new ProviderUnavailable("Provider communication failure for model " + model, e);
        } catch (Exception e) {
            throw new ProviderUnavailable("Unexpected provider error for model " + model, e);
        }
    }

    private Map<String, Object> buildPayload(ChatRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        payload.put("messages", messages);

        if (request.maxOutputTokens() != null && request.maxOutputTokens() > 0) {
            payload.put("max_tokens", request.maxOutputTokens());
        }
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.wantJson()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            payload.put("reasoning_effort", reasoningEffort);
        }
        if (baseUrl.contains("groq.com")) {
            payload.put("reasoning_format", "parsed");
        }
        return payload;
    }

    private ChatResult parseChatResult(JsonNode root) {
        if (!root.has("choices") || !root.get("choices").isArray() || root.get("choices").isEmpty()) {
            throw new ProviderUnavailable("Malformed provider response: no choices found for model " + model);
        }

        JsonNode choiceNode = root.get("choices").get(0);
        JsonNode messageNode = choiceNode.get("message");
        String text = (messageNode != null && messageNode.has("content")) ? messageNode.get("content").asText() : "";
        String returnedModel = root.has("model") ? root.get("model").asText() : model;
        String finishReason = choiceNode.has("finish_reason") ? choiceNode.get("finish_reason").asText() : null;

        TokenUsage usage = null;
        if (root.has("usage") && root.get("usage").isObject()) {
            JsonNode u = root.get("usage");
            Integer reasoningTokens = null;
            if (u.has("completion_tokens_details") && u.get("completion_tokens_details").has("reasoning_tokens")) {
                reasoningTokens = u.get("completion_tokens_details").get("reasoning_tokens").asInt();
            }
            usage = new TokenUsage(
                    u.has("prompt_tokens") ? u.get("prompt_tokens").asInt() : null,
                    u.has("completion_tokens") ? u.get("completion_tokens").asInt() : null,
                    u.has("total_tokens") ? u.get("total_tokens").asInt() : null,
                    reasoningTokens
            );
        }

        return new ChatResult(text, returnedModel != null ? returnedModel : model, usage, finishReason);
    }

    private Long parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            try {
                Instant instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(header.trim()));
                long seconds = Duration.between(Instant.now(), instant).toSeconds();
                return seconds > 0 ? seconds : 0L;
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
