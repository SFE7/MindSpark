package com.mindspark.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SubscriptionServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }

    @Bean
    public ApplicationRunner internalSecretCheck(
            @Value("${mindspark.internal.service-secret:${INTERNAL_SERVICE_SECRET:mindspark-internal-dev-key}}") String internalSecret) {
        return args -> {
            if ("mindspark-internal-dev-key".equals(internalSecret)) {
                log.warn("SECURITY WARNING: Using default development internal service secret. Please configure a strong, unique INTERNAL_SERVICE_SECRET in production.");
            }
        };
    }
}
