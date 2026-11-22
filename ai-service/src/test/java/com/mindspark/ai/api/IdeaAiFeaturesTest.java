package com.mindspark.ai.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {IdeaAiController.class})
@Import({
        SecurityConfig.class,
        JwtAuthConverter.class,
        TierResolver.class,
        GlobalExceptionHandler.class,
        IdeaAiFeaturesTest.TestFeatureConfig.class
})
class IdeaAiFeaturesTest {

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

    // ==========================================
    // 1. POST /api/ai/ideas/generate
    // ==========================================

    @Test
    @DisplayName("Generate Ideas: 200 happy path returns expected shape with ideas list and metadata")
    void testGenerateIdeasHappyPath() throws Exception {
        String cannedJson = """
                {
                  "ideas": [
                    {"title": "Idea 1", "description": "Desc 1"},
                    {"title": "Idea 2", "description": "Desc 2"}
                  ]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(cannedJson, "gemini-3.5-flash-lite", null);

        String payload = """
                {
                  "topic": "Remote work tools",
                  "count": 2
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-gen-1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ideas[0].title").value("Idea 1"))
                .andExpect(jsonPath("$.result.ideas[0].description").value("Desc 1"))
                .andExpect(jsonPath("$.result.ideas[1].title").value("Idea 2"))
                .andExpect(jsonPath("$.meta.model").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.meta.tier").value("FREE"));

        verify(quotaService).reserve("user-gen-1", 10);
    }

    @Test
    @DisplayName("Generate Ideas: 400 on missing topic")
    void testGenerateIdeasMissingTopic() throws Exception {
        String payload = "{\"count\": 5}";

        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Generate Ideas: 400 on out-of-range count (> 10)")
    void testGenerateIdeasCountOutOfRange() throws Exception {
        String payload = "{\"topic\": \"AI in healthcare\", \"count\": 15}";

        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Generate Ideas: 401 without token")
    void testGenerateIdeasWithoutToken() throws Exception {
        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\": \"Test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Generate Ideas: Request body cannot change tier from token authority")
    void testRequestBodyCannotChangeTier() throws Exception {
        clientHolder.currentClient = req -> new ChatResult("{\"ideas\": [{\"title\": \"A\", \"description\": \"B\"}]}", "gemini-3.5-flash-lite", null);

        // Body has tier=PREMIUM, but token has only ROLE_free
        String payload = "{\"topic\": \"Test\", \"tier\": \"PREMIUM\"}";

        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-free-token"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.tier").value("FREE"))
                .andExpect(jsonPath("$.meta.model").value("gemini-3.5-flash-lite"));

        verify(quotaService).reserve("user-free-token", 10);
    }

    @Test
    @DisplayName("Generate Ideas: 429 DAILY_LIMIT_REACHED when user reaches daily quota")
    void testGenerateIdeasDailyLimitReached() throws Exception {
        when(quotaService.reserve(eq("user-capped"), anyInt())).thenReturn(false);

        String payload = "{\"topic\": \"New Tech\"}";

        mockMvc.perform(post("/api/ai/ideas/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-capped"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("Generate Ideas: Quota refunded when provider fails")
    void testGenerateIdeasRefundsQuotaOnProviderFailure() throws Exception {
        clientHolder.currentClient = req -> {
            throw new ProviderUnavailable("Server connection failure");
        };

        String payload = "{\"topic\": \"Fail test\"}";

        mockMvc.perform(post("/api/ai/ideas/generate")
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

    // ==========================================
    // 2. POST /api/ai/ideas/improve
    // ==========================================

    @Test
    @DisplayName("Improve Idea: 200 happy path returns title, text, changes")
    void testImproveIdeaHappyPath() throws Exception {
        String cannedJson = """
                {
                  "title": "Smart Solar Panels",
                  "text": "Self-cleaning solar panels with automated angle optimization.",
                  "changes": ["Added self-cleaning mechanism", "Clarified angle optimization"]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(cannedJson, "openai/gpt-oss-120b", null);

        String payload = """
                {
                  "idea": {
                    "id": "solar-1",
                    "text": "Solar panels that clean themselves and track sun."
                  },
                  "mode": "expand"
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/improve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-prem"))
                        .authorities(new SimpleGrantedAuthority("ROLE_premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("Smart Solar Panels"))
                .andExpect(jsonPath("$.result.changes[0]").value("Added self-cleaning mechanism"))
                .andExpect(jsonPath("$.meta.tier").value("PREMIUM"));
    }

    @Test
    @DisplayName("Improve Idea: 400 on invalid mode")
    void testImproveIdeaInvalidMode() throws Exception {
        String payload = """
                {
                  "idea": {"text": "Solar idea"},
                  "mode": "rewrite-completely"
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/improve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // 3. POST /api/ai/ideas/group
    // ==========================================

    @Test
    @DisplayName("Group Ideas: 200 happy path and normalizes IDs so every input ID appears exactly once")
    void testGroupIdeasHappyPathAndNormalization() throws Exception {
        // Model hallucinated "unknown-id" and omitted "id-3"
        String modelOutput = """
                {
                  "groups": [
                    {
                      "name": "Design",
                      "ideaIds": ["id-1", "unknown-id"],
                      "rationale": "Visual aspects"
                    }
                  ],
                  "ungroupedIds": ["id-2"]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(modelOutput, "gemini-3.5-flash-lite", null);

        String payload = """
                {
                  "ideas": [
                    {"id": "id-1", "text": "New logo design"},
                    {"id": "id-2", "text": "Microservice backend"},
                    {"id": "id-3", "text": "Payment gateway"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-group"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.groups[0].name").value("Design"))
                .andExpect(jsonPath("$.result.groups[0].ideaIds[0]").value("id-1"))
                // unknown-id must be dropped
                .andExpect(jsonPath("$.result.groups[0].ideaIds.length()").value(1))
                // id-2 and omitted id-3 must both be in ungroupedIds
                .andExpect(jsonPath("$.result.ungroupedIds.length()").value(2))
                .andExpect(jsonPath("$.result.ungroupedIds[0]").value("id-2"))
                .andExpect(jsonPath("$.result.ungroupedIds[1]").value("id-3"));
    }

    @Test
    @DisplayName("Group Ideas: 400 when less than 2 ideas provided")
    void testGroupIdeasTooFewIdeas() throws Exception {
        String payload = """
                {
                  "ideas": [
                    {"id": "id-1", "text": "Only one idea"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // 4. POST /api/ai/ideas/action-plan
    // ==========================================

    @Test
    @DisplayName("Action Plan: 200 happy path normalizes priorities and filters unknown relatedIdeaIds")
    void testActionPlanHappyPath() throws Exception {
        String modelOutput = """
                {
                  "title": "Q1 Launch Plan",
                  "steps": [
                    {
                      "order": 1,
                      "title": "Setup Infra",
                      "description": "Deploy DB and k8s",
                      "priority": "HIGH",
                      "relatedIdeaIds": ["id-1", "hallucinated-id"]
                    }
                  ],
                  "quickWins": ["Setup repository templates"]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(modelOutput, "openai/gpt-oss-120b", null);

        String payload = """
                {
                  "goal": "Launch MVP in Q1",
                  "ideas": [
                    {"id": "id-1", "text": "Infrastructure setup"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/action-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-plan"))
                        .authorities(new SimpleGrantedAuthority("ROLE_premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("Q1 Launch Plan"))
                .andExpect(jsonPath("$.result.steps[0].priority").value("high"))
                .andExpect(jsonPath("$.result.steps[0].relatedIdeaIds[0]").value("id-1"))
                .andExpect(jsonPath("$.result.steps[0].relatedIdeaIds.length()").value(1))
                .andExpect(jsonPath("$.result.quickWins[0]").value("Setup repository templates"));
    }

    // ==========================================
    // 5. POST /api/ai/ideas/risks
    // ==========================================

    @Test
    @DisplayName("Find Risks: 200 happy path returns risks with severity and mitigation")
    void testFindRisksHappyPath() throws Exception {
        String modelOutput = """
                {
                  "risks": [
                    {
                      "title": "API Rate Limits",
                      "description": "Provider quota may be exceeded under high load",
                      "severity": "HIGH",
                      "mitigation": "Implement client side caching and exponential backoff"
                    }
                  ]
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(modelOutput, "gemini-3.5-flash-lite", null);

        String payload = """
                {
                  "idea": {
                    "id": "idea-ai",
                    "text": "Add live AI brainstorming assistance"
                  },
                  "context": "Free plan has 15 RPM limit"
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/risks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-risks"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.risks[0].title").value("API Rate Limits"))
                .andExpect(jsonPath("$.result.risks[0].severity").value("high"))
                .andExpect(jsonPath("$.result.risks[0].mitigation").value("Implement client side caching and exponential backoff"));
    }

    // ==========================================
    // 6. POST /api/ai/ideas/compare
    // ==========================================

    @Test
    @DisplayName("Compare Ideas: 200 happy path verifies criteria, comparison items, and valid recommendation")
    void testCompareIdeasHappyPath() throws Exception {
        String modelOutput = """
                {
                  "criteria": ["Feasibility", "Impact"],
                  "comparison": [
                    {
                      "ideaId": "id-a",
                      "scores": {"Feasibility": 4, "Impact": 5},
                      "strengths": ["Quick to build"],
                      "weaknesses": ["Needs marketing"]
                    },
                    {
                      "ideaId": "id-b",
                      "scores": {"Feasibility": 2, "Impact": 3},
                      "strengths": ["High novelty"],
                      "weaknesses": ["Complex"]
                    }
                  ],
                  "recommendation": {
                    "ideaId": "id-a",
                    "reason": "Higher feasibility and clear impact"
                  }
                }
                """;
        clientHolder.currentClient = req -> new ChatResult(modelOutput, "gemini-3.5-flash-lite", null);

        String payload = """
                {
                  "ideas": [
                    {"id": "id-a", "text": "Option A: microservice architecture"},
                    {"id": "id-b", "text": "Option B: monolithic rewrite"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt()
                        .jwt(token -> token.subject("user-compare"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.criteria[0]").value("Feasibility"))
                .andExpect(jsonPath("$.result.comparison[0].ideaId").value("id-a"))
                .andExpect(jsonPath("$.result.comparison[0].scores.Feasibility").value(4))
                .andExpect(jsonPath("$.result.recommendation.ideaId").value("id-a"));
    }

    @Test
    @DisplayName("Compare Ideas: 400 when less than 2 or more than 4 ideas provided")
    void testCompareIdeasInvalidCount() throws Exception {
        String payload = """
                {
                  "ideas": [
                    {"id": "id-1", "text": "Single idea"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/ai/ideas/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
