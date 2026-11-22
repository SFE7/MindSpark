package com.mindspark.ai.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats/ai")
public class AdminAiStatsController {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AdminAiStatsController(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<AdminAiStatsResponse> getAiStats() {
        LocalDate today = LocalDate.now(clock);

        Integer callsToday = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(request_count), 0) FROM ai_usage WHERE usage_day = ?",
                Integer.class,
                today
        );

        Integer totalCalls = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(request_count), 0) FROM ai_usage",
                Integer.class
        );

        Integer activeUsersToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM ai_usage WHERE usage_day = ? AND request_count > 0",
                Integer.class,
                today
        );

        int todayCount = (callsToday != null) ? callsToday : 0;
        int allTimeCount = (totalCalls != null) ? totalCalls : 0;
        int activeCount = (activeUsersToday != null) ? activeUsersToday : 0;

        Map<String, Integer> tierBreakdown = new LinkedHashMap<>();
        if (todayCount > 0) {
            tierBreakdown.put("TOTAL", todayCount);
        }

        return ResponseEntity.ok(new AdminAiStatsResponse(
                todayCount,
                allTimeCount,
                activeCount,
                tierBreakdown
        ));
    }
}
