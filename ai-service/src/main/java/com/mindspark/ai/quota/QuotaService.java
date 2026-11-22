package com.mindspark.ai.quota;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class QuotaService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final boolean isPostgres;

    public QuotaService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.isPostgres = determineIfPostgres(jdbcTemplate);
    }

    private static boolean determineIfPostgres(JdbcTemplate jdbcTemplate) {
        try {
            if (jdbcTemplate.getDataSource() != null) {
                try (var conn = jdbcTemplate.getDataSource().getConnection()) {
                    String name = conn.getMetaData().getDatabaseProductName();
                    return name != null && name.toLowerCase().contains("postgres");
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine database product name, defaulting to PostgreSQL: {}", e.getMessage());
        }
        return true;
    }

    /**
     * Atomically reserves 1 request count for the user on today's UTC day.
     * Returns true if reserved successfully, false if the daily limit is reached.
     */
    public boolean reserve(String userId, int limit) {
        if (userId == null || userId.isBlank() || limit <= 0) {
            return false;
        }

        LocalDate today = LocalDate.now(clock);

        // 1. Ensure the user's daily row exists (PostgreSQL ON CONFLICT DO NOTHING, or H2 INSERT WHERE NOT EXISTS)
        ensureRowExists(userId, today);

        // 2. Atomically increment request_count only if below limit
        int updated = jdbcTemplate.update(
                "UPDATE ai_usage SET request_count = request_count + 1 " +
                "WHERE user_id = ? AND usage_day = ? AND request_count < ?",
                userId, today, limit
        );

        return updated > 0;
    }

    private void ensureRowExists(String userId, LocalDate today) {
        if (isPostgres) {
            jdbcTemplate.update(
                    "INSERT INTO ai_usage (user_id, usage_day, request_count) VALUES (?, ?, 0) " +
                    "ON CONFLICT (user_id, usage_day) DO NOTHING",
                    userId, today
            );
        } else {
            try {
                jdbcTemplate.update(
                        "INSERT INTO ai_usage (user_id, usage_day, request_count) " +
                        "SELECT ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM ai_usage WHERE user_id = ? AND usage_day = ?)",
                        userId, today, userId, today
                );
            } catch (DataIntegrityViolationException ignored) {
                // In H2, ignore concurrent insert duplicate key
            }
        }
    }

    /**
     * Decrements the user's request count for today when an AI call fails, never dropping below 0.
     */
    public void refund(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        jdbcTemplate.update(
                "UPDATE ai_usage SET request_count = request_count - 1 WHERE user_id = ? AND usage_day = ? AND request_count > 0",
                userId, today
        );
        log.debug("Refunded quota for user on date {}", today);
    }

    /**
     * Gets today's used request count for the given user.
     */
    public int getUsageToday(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }

        LocalDate today = LocalDate.now(clock);
        List<Integer> counts = jdbcTemplate.query(
                "SELECT request_count FROM ai_usage WHERE user_id = ? AND usage_day = ?",
                (rs, rowNum) -> rs.getInt(1),
                userId, today
        );

        return counts.isEmpty() ? 0 : counts.get(0);
    }
}
