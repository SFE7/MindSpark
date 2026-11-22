package com.mindspark.ai.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.common.exception.GlobalExceptionHandler;
import com.mindspark.ai.common.orchestrator.AiOrchestrator;
import com.mindspark.ai.common.util.StructuredOutputHelper;
import com.mindspark.ai.config.AiCapsProperties;
import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.provider.client.LlmClient;
import com.mindspark.ai.provider.client.LlmClientFactory;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatResult;
import com.mindspark.ai.provider.router.ModelRouter;
import com.mindspark.ai.quota.QuotaService;
import com.mindspark.ai.security.JwtAuthConverter;
import com.mindspark.ai.security.SecurityConfig;
import com.mindspark.ai.tier.TierResolver;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SessionAiController.class})
@Import({
        SecurityConfig.class,
        JwtAuthConverter.class,
        TierResolver.class,
        GlobalExceptionHandler.class,
        SessionAiFeaturesTest.TestFeatureConfig.class
})
class SessionAiFeaturesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private TestLlmClientHolder clientHolder;

    static class TestLlmClientHolder {
        LlmClient currentClient;
    }

    @TestConfiguration
    static class TestFeatureConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mocked decoder");
            };
        }

        @Bean
        AiTiersProperties aiTiersProperties() {
            return new AiTiersProperties();
        }

        @Bean
        AiCapsProperties aiCapsProperties() {
            return new AiCapsProperties();
        }

        @Bean
        QuotaService quotaService() {
            return Mockito.mock(QuotaService.class);
        }

        @Bean
        TestLlmClientHolder testLlmClientHolder() {
            return new TestLlmClientHolder();
        }

        @Bean
        LlmClientFactory llmClientFactory(TestLlmClientHolder holder) {
            return (model, tier) -> holder.currentClient;
        }

        @Bean
        ModelRouter modelRouter(AiTiersProperties properties, LlmClientFactory factory) {
            return new ModelRouter(properties, factory);
        }

        @Bean
        StructuredOutputHelper structuredOutputHelper() {
            return new StructuredOutputHelper(new ObjectMapper());
        }

        @Bean
        AiOrchestrator aiOrchestrator(
                TierResolver tierResolver,
                AiTiersProperties tiersProperties,
                AiCapsProperties capsProperties,
                QuotaService quotaService,
                ModelRouter modelRouter,
                StructuredOutputHelper structuredOutputHelper) {
            return new AiOrchestrator(tierResolver, tiersProperties, capsProperties, quotaService, modelRouter, structuredOutputHelper);
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(quotaService);
        when(quotaService.reserve(any(), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("Summarize Session: 200 happy path returns summary, themes, topIdeas, openQuestions")
    void testSummarizeSessionHappyPath() throws Exception {
        String modelOutput = """
                {
                  "summary": "Team aligned on adopting microservices with Kafka event streaming.",
                  "themes": ["Architecture", "Scalability"],
                  "topIdeas": [
                    {"id": "idea-1", "reason": "Highest votes and low migration complexity"},
                    {"id": "hallucinated-id", "reason": "Ignored"}
                  ],
                  "openQuestions": ["What is our latency SLA?"]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(modelOutput, "openai/gpt-oss-120b", null);

        String payload = """
                {
                  "sessionTitle": "Q4 Architecture Planning",
                  "ideas": [
                    {"id": "idea-1", "text": "Migrate to microservices", "votes": 5, "category": "Core"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/sessions/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-session"))
                        .authorities(new SimpleGrantedAuthority("ROLE_premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary").value("Team aligned on adopting microservices with Kafka event streaming."))
                .andExpect(jsonPath("$.result.themes[0]").value("Architecture"))
                .andExpect(jsonPath("$.result.topIdeas[0].id").value("idea-1"))
                // hallucinated-id must be dropped
                .andExpect(jsonPath("$.result.topIdeas.length()").value(1))
                .andExpect(jsonPath("$.result.openQuestions[0]").value("What is our latency SLA?"))
                .andExpect(jsonPath("$.meta.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.meta.model").value("openai/gpt-oss-120b"));

        verify(quotaService).reserve("user-session", 20);
    }

    @Test
    @DisplayName("Summarize Session: 400 when ideas list is empty")
    void testSummarizeEmptyIdeas() throws Exception {
        String payload = """
                {
                  "sessionTitle": "Empty Session",
                  "ideas": []
                }
                """;

        mockMvc.perform(post("/api/ai/sessions/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Summarize Session: 401 without authentication token")
    void testSummarizeWithoutToken() throws Exception {
        mockMvc.perform(post("/api/ai/sessions/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ideas\": [{\"id\":\"1\", \"text\":\"hello\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Summarize Session: 429 DAILY_LIMIT_REACHED when user daily quota is exhausted")
    void testSummarizeDailyLimitReached() throws Exception {
        when(quotaService.reserve(eq("user-capped"), anyInt())).thenReturn(false);

        String payload = """
                {
                  "ideas": [
                    {"id": "idea-1", "text": "Migrate to microservices"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/sessions/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-capped"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("Summarize Session: Quota is refunded when provider fails")
    void testSummarizeRefundsOnFailure() throws Exception {
        clientHolder.currentClient = req -> {
            throw new ProviderUnavailable("LLM service down");
        };

        String payload = """
                {
                  "ideas": [
                    {"id": "idea-1", "text": "Migrate to microservices"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/sessions/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-refund"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"));

        verify(quotaService).reserve("user-refund", 10);
        verify(quotaService).refund("user-refund");
    }
}
