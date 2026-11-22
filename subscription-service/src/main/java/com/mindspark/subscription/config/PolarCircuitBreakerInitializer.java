package com.mindspark.subscription.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Configuration
public class PolarCircuitBreakerInitializer {

    @Bean
    public ApplicationRunner initPolarCircuitBreaker(CircuitBreakerRegistry registry) {
        return args -> registry.circuitBreaker("polarApi");
    }
}
