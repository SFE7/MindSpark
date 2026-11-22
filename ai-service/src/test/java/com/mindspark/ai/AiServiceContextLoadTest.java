package com.mindspark.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.api.IdeaAiController;
import com.mindspark.ai.api.SessionAiController;
import com.mindspark.ai.common.orchestrator.AiOrchestrator;
import com.mindspark.ai.provider.router.ModelRouter;
import com.mindspark.ai.quota.QuotaService;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceContextLoadTest {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private ModelRouter modelRouter;

    @Autowired
    private AiOrchestrator aiOrchestrator;

    @Autowired
    private IdeaAiController ideaAiController;

    @Autowired
    private SessionAiController sessionAiController;

    @TestConfiguration
    static class MockJwtDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mocked decoder for test");
            };
        }
    }

    @Test
    @DisplayName("Application context loads completely and all critical beans are created")
    void contextLoads() {
        assertNotNull(restClientBuilder, "RestClient.Builder must be created");
        assertNotNull(objectMapper, "ObjectMapper must be created");
        assertNotNull(quotaService, "QuotaService must be created");
        assertNotNull(modelRouter, "ModelRouter must be created");
        assertNotNull(aiOrchestrator, "AiOrchestrator must be created");
        assertNotNull(ideaAiController, "IdeaAiController must be created");
        assertNotNull(sessionAiController, "SessionAiController must be created");
    }
}
