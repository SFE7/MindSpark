package com.mindspark.ai.quota;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaServiceTest {

    private JdbcTemplate jdbcTemplate;
    private MutableClock mutableClock;
    private QuotaService quotaService;

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:quotatest_" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ai_usage (
                user_id VARCHAR(255) NOT NULL,
                usage_day DATE NOT NULL,
                request_count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, usage_day)
            )
        """);

        Instant startInstant = Instant.parse("2026-11-19T10:00:00Z");
        mutableClock = new MutableClock(startInstant, ZoneOffset.UTC);
        quotaService = new QuotaService(jdbcTemplate, mutableClock);
    }

    @Test
    @DisplayName("Limit reached at exactly N requests")
    void testLimitReachedAtExactlyN() {
        String userId = "user-limit-test";
        int limit = 5;

        for (int i = 1; i <= limit; i++) {
            boolean reserved = quotaService.reserve(userId, limit);
            assertTrue(reserved, "Reservation " + i + " should succeed");
            assertEquals(i, quotaService.getUsageToday(userId));
        }

        boolean overflow = quotaService.reserve(userId, limit);
        assertFalse(overflow, "Reservation after limit should fail");
        assertEquals(limit, quotaService.getUsageToday(userId));
    }

    @Test
    @DisplayName("Per-user quota isolation")
    void testPerUserIsolation() {
        String userA = "user-a";
        String userB = "user-b";
        int limit = 3;

        for (int i = 0; i < limit; i++) {
            assertTrue(quotaService.reserve(userA, limit));
        }
        assertFalse(quotaService.reserve(userA, limit));

        // User B must still have full quota
        assertEquals(0, quotaService.getUsageToday(userB));
        for (int i = 0; i < limit; i++) {
            assertTrue(quotaService.reserve(userB, limit));
        }
        assertFalse(quotaService.reserve(userB, limit));
    }

    @Test
    @DisplayName("UTC day rollover resets quota")
    void testUtcDayRollover() {
        String userId = "user-rollover-test";
        int limit = 2;

        assertTrue(quotaService.reserve(userId, limit));
        assertTrue(quotaService.reserve(userId, limit));
        assertFalse(quotaService.reserve(userId, limit));
        assertEquals(2, quotaService.getUsageToday(userId));

        // Roll over clock to next day
        mutableClock.setInstant(Instant.parse("2026-11-20T00:00:01Z"));

        assertEquals(0, quotaService.getUsageToday(userId), "Usage on new day should be 0");
        assertTrue(quotaService.reserve(userId, limit), "Reservation on new day should succeed");
        assertEquals(1, quotaService.getUsageToday(userId));
    }

    @Test
    @DisplayName("Refund decrements usage count and never goes below zero")
    void testRefundNeverGoesBelowZero() {
        String userId = "user-refund-test";
        int limit = 5;

        // Refund on user with 0 usage should stay 0
        quotaService.refund(userId);
        assertEquals(0, quotaService.getUsageToday(userId));

        assertTrue(quotaService.reserve(userId, limit));
        assertTrue(quotaService.reserve(userId, limit));
        assertEquals(2, quotaService.getUsageToday(userId));

        quotaService.refund(userId);
        assertEquals(1, quotaService.getUsageToday(userId));

        quotaService.refund(userId);
        assertEquals(0, quotaService.getUsageToday(userId));

        // Additional refund does not go negative
        quotaService.refund(userId);
        assertEquals(0, quotaService.getUsageToday(userId));
    }

    @Test
    @DisplayName("Concurrency: 25 parallel reserve() calls with limit 10 results in exactly 10 true and 15 false")
    void testConcurrency25ThreadsLimit10() throws InterruptedException {
        String userId = "user-concurrent";
        int limit = 10;
        int threadCount = 25;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean result = quotaService.reserve(userId, limit);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All threads should complete within 10s");
        assertEquals(10, successCount.get(), "Exactly 10 reservations should succeed");
        assertEquals(15, failCount.get(), "Exactly 15 reservations should be rejected");
        assertEquals(10, quotaService.getUsageToday(userId));
    }
}
