package com.mindspark.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        assertThat(environmentRepository).isNotNull();
    }

    @Test
    @DisplayName("Config Server native repo serves subscription-service properties including polarApi")
    void testServesSubscriptionServiceConfig() {
        Environment env = environmentRepository.findOne("subscription-service", "default", null);
        assertThat(env).isNotNull();
        assertThat(env.getName()).isEqualTo("subscription-service");

        // Verify shared or service properties are served
        boolean hasPolarUrl = env.getPropertySources().stream()
                .anyMatch(ps -> ps.getSource().containsKey("mindspark.polar.api-base-url"));
        assertThat(hasPolarUrl).isTrue();

        boolean hasPolarBreaker = env.getPropertySources().stream()
                .anyMatch(ps -> ps.getSource().containsKey("resilience4j.circuitbreaker.instances.polarApi.slidingWindowSize"));
        assertThat(hasPolarBreaker).isTrue();
    }

    @Test
    @DisplayName("Config Server native repo serves ai-service properties including aiExternalProvider")
    void testServesAiServiceConfig() {
        Environment env = environmentRepository.findOne("ai-service", "default", null);
        assertThat(env).isNotNull();
        assertThat(env.getName()).isEqualTo("ai-service");

        boolean hasAiConcurrency = env.getPropertySources().stream()
                .anyMatch(ps -> ps.getSource().containsKey("mindspark.ai.max-concurrent-upstream-calls"));
        assertThat(hasAiConcurrency).isTrue();

        boolean hasAiBreaker = env.getPropertySources().stream()
                .anyMatch(ps -> ps.getSource().containsKey("resilience4j.circuitbreaker.instances.aiExternalProvider.waitDurationInOpenState"));
        assertThat(hasAiBreaker).isTrue();
    }
}
