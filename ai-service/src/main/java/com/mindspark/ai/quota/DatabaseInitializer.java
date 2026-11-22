package com.mindspark.ai.quota;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_usage (
                    user_id VARCHAR(255) NOT NULL,
                    usage_day DATE NOT NULL,
                    request_count INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, usage_day)
                )
            """);
            log.info("Verified ai_usage table exists in database.");
        } catch (Exception e) {
            log.warn("DatabaseInitializer table check warning: {}", e.getMessage());
        }
    }
}
