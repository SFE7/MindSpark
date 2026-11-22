package com.mindspark.subscription.polar.client;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspark.subscription.polar.config.PolarProperties;
import com.mindspark.subscription.polar.dto.PolarCheckoutDetails;
import com.mindspark.subscription.polar.dto.PolarCheckoutResponse;
import com.mindspark.subscription.polar.exception.PolarClientException;
import com.mindspark.subscription.polar.exception.PolarUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PolarCheckoutClient {

    private final PolarProperties polarProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PolarCheckoutClient(
            PolarProperties polarProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.polarProperties = polarProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        RestClient.Builder builder = (restClientBuilder != null) ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(polarProperties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @CircuitBreaker(name = "polarApi", fallbackMethod = "createCheckoutFallback")
    public PolarCheckoutResponse createCheckout(
            String productId,
            String successUrl,
            String returnUrl,
            String externalCustomerId,
            String customerEmail,
            String planCode) {

        Map<String, Object> body = new HashMap<>();
        body.put("products", List.of(productId));
        body.put("success_url", successUrl);
        body.put("return_url", returnUrl);
        if (externalCustomerId != null && !externalCustomerId.isBlank()) {
            body.put("external_customer_id", externalCustomerId);
        }
        if (customerEmail != null && !customerEmail.isBlank()) {
            body.put("customer_email", customerEmail);
        }

        Map<String, Object> metadata = new HashMap<>();
        if (externalCustomerId != null) {
            metadata.put("user_id", externalCustomerId);
        }
        if (planCode != null) {
            metadata.put("plan_code", planCode);
        }
        body.put("metadata", metadata);

        log.info("Creating Polar checkout for plan={}, customerId={}", planCode, externalCustomerId);

        String responseBody = restClient.post()
                .uri("/v1/checkouts/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + polarProperties.getAccessToken())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    String errText = new String(resp.getBody().readAllBytes());
                    log.warn("Polar API returned 4xx status {}: {}", resp.getStatusCode(), errText);
                    throw new PolarClientException("Polar API client error: " + resp.getStatusCode() + " - " + errText);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    String errText = new String(resp.getBody().readAllBytes());
                    log.error("Polar API returned 5xx status {}: {}", resp.getStatusCode(), errText);
                    throw new PolarUnavailableException("Polar API server error: " + resp.getStatusCode());
                })
                .body(String.class);

        return parseCheckoutResponse(responseBody, productId);
    }

    @CircuitBreaker(name = "polarApi", fallbackMethod = "getCheckoutFallback")
    public PolarCheckoutDetails getCheckout(String checkoutId) {
        log.info("Fetching Polar checkout details for checkoutId={}", checkoutId);

        String responseBody = restClient.get()
                .uri("/v1/checkouts/{checkoutId}", checkoutId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + polarProperties.getAccessToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    String errText = new String(resp.getBody().readAllBytes());
                    log.warn("Polar API returned 4xx status {}: {}", resp.getStatusCode(), errText);
                    throw new PolarClientException("Polar API client error: " + resp.getStatusCode() + " - " + errText);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    String errText = new String(resp.getBody().readAllBytes());
                    log.error("Polar API returned 5xx status {}: {}", resp.getStatusCode(), errText);
                    throw new PolarUnavailableException("Polar API server error: " + resp.getStatusCode());
                })
                .body(String.class);

        return parseCheckoutDetails(responseBody);
    }

    public PolarCheckoutResponse createCheckoutFallback(
            String productId,
            String successUrl,
            String returnUrl,
            String externalCustomerId,
            String customerEmail,
            String planCode,
            Throwable t) {
        handleFallback(t);
        return null;
    }

    public PolarCheckoutDetails getCheckoutFallback(String checkoutId, Throwable t) {
        handleFallback(t);
        return null;
    }

    private void handleFallback(Throwable t) {
        log.error("Polar API operation failed or circuit breaker active: {}", t.getMessage());
        if (t instanceof PolarClientException pce) {
            throw pce;
        }
        if (t instanceof CallNotPermittedException) {
            throw new PolarUnavailableException("Payment provider circuit breaker is OPEN. Please retry shortly.", t);
        }
        throw new PolarUnavailableException("Payment provider is temporarily unavailable: " + t.getMessage(), t);
    }

    private PolarCheckoutResponse parseCheckoutResponse(String json, String defaultProductId) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String id = root.path("id").asText(null);
            String url = root.path("url").asText(null);
            String status = root.path("status").asText("open");

            String productId = defaultProductId;
            JsonNode productsNode = root.path("products");
            if (productsNode.isArray() && !productsNode.isEmpty()) {
                JsonNode first = productsNode.get(0);
                if (first.isObject() && first.hasNonNull("id")) {
                    productId = first.path("id").asText();
                } else if (first.isTextual()) {
                    productId = first.asText();
                }
            } else if (root.hasNonNull("product_id")) {
                productId = root.path("product_id").asText();
            }

            return new PolarCheckoutResponse(id, url, status, productId);
        } catch (Exception e) {
            log.error("Failed to parse Polar checkout creation response: {}", e.getMessage());
            throw new PolarUnavailableException("Failed to parse checkout creation response from Polar", e);
        }
    }

    private PolarCheckoutDetails parseCheckoutDetails(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String id = root.path("id").asText(null);
            String status = root.path("status").asText("unknown");

            int amount = 0;
            if (root.hasNonNull("total_amount")) {
                amount = root.path("total_amount").asInt();
            } else if (root.hasNonNull("amount")) {
                amount = root.path("amount").asInt();
            }

            String currency = root.path("currency").asText("USD");
            String externalCustomerId = root.hasNonNull("external_customer_id")
                    ? root.path("external_customer_id").asText()
                    : null;
            String customerEmail = root.hasNonNull("customer_email")
                    ? root.path("customer_email").asText()
                    : null;

            Map<String, Object> metadata = new HashMap<>();
            JsonNode metadataNode = root.path("metadata");
            if (metadataNode.isObject()) {
                metadataNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        metadata.put(entry.getKey(), entry.getValue().asText());
                    } else if (entry.getValue().isNumber()) {
                        metadata.put(entry.getKey(), entry.getValue().numberValue());
                    } else if (entry.getValue().isBoolean()) {
                        metadata.put(entry.getKey(), entry.getValue().asBoolean());
                    } else {
                        metadata.put(entry.getKey(), entry.getValue().toString());
                    }
                });
            }

            String productId = null;
            JsonNode productsNode = root.path("products");
            if (productsNode.isArray() && !productsNode.isEmpty()) {
                JsonNode first = productsNode.get(0);
                if (first.isObject() && first.hasNonNull("id")) {
                    productId = first.path("id").asText();
                } else if (first.isTextual()) {
                    productId = first.asText();
                }
            } else if (root.hasNonNull("product_id")) {
                productId = root.path("product_id").asText();
            }

            return new PolarCheckoutDetails(
                    id,
                    status,
                    productId,
                    amount,
                    currency,
                    externalCustomerId,
                    customerEmail,
                    Collections.unmodifiableMap(metadata)
            );
        } catch (Exception e) {
            log.error("Failed to parse Polar checkout details response: {}", e.getMessage());
            throw new PolarUnavailableException("Failed to parse checkout details from Polar", e);
        }
    }
}
