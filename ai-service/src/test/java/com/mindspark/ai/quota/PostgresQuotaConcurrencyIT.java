package com.mindspark.ai.quota;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@EnabledIfSystemProperty(named = "run.postgres.it", matches = "true")
class PostgresQuotaConcurrencyIT {

    private static JdbcTemplate jdbcTemplate;
    private static DataSourceTransactionManager transactionManager;
    private QuotaService quotaService;
    private Clock fixedClock;

    @BeforeAll
    static void initDataSource() {
        String host = System.getProperty("test.postgres.host", "localhost");
        String port = System.getProperty("test.postgres.port", "5434");
        String dbName = System.getProperty("test.postgres.db", "ai_quota_test");
        String user = System.getProperty("test.postgres.user", "ai_user");
        String password = System.getProperty("test.postgres.password");
        if (password == null || password.isBlank()) {
            password = System.getenv("AI_DB_PASSWORD");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        dataSource.setUsername(user);
        dataSource.setPassword(password);

        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ai_usage (
                user_id VARCHAR(255) NOT NULL,
                usage_day DATE NOT NULL,
                request_count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, usage_day)
            )
        """);
    }

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);
        quotaService = new QuotaService(jdbcTemplate, fixedClock);
    }

    @Test
    @DisplayName("PostgreSQL Concurrency: 25 parallel threads, limit 10, yields exactly 10 true and 15 false")
    void testPostgresConcurrency25ThreadsLimit10() throws InterruptedException {
        String userId = "pg-concurrent-user-" + System.currentTimeMillis();
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

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All 25 threads should complete within 15 seconds");
        assertEquals(10, successCount.get(), "Exactly 10 threads must succeed");
        assertEquals(15, failCount.get(), "Exactly 15 threads must fail");
        assertEquals(10, quotaService.getUsageToday(userId));
    }

    @Test
    @DisplayName("PostgreSQL First-Request-Of-Day Race: 10 parallel threads with fresh user and limit 1")
    void testPostgresFirstRequestOfDayRaceFreshUser() throws InterruptedException {
        String userId = "pg-first-request-user-" + System.currentTimeMillis();
        int limit = 1;
        int threadCount = 10;

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

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All threads should complete within 15s");
        assertEquals(1, successCount.get(), "Exactly 1 thread should succeed on limit 1 first request of day");
        assertEquals(9, failCount.get(), "Remaining 9 threads should fail");
        assertEquals(1, quotaService.getUsageToday(userId));
    }

    @Test
    @DisplayName("PostgreSQL Inside Active Transaction: ON CONFLICT DO NOTHING does not abort transaction")
    void testPostgresInsideActiveTransaction() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        String userId = "pg-tx-user-" + System.currentTimeMillis();

        Boolean result = txTemplate.execute(status -> {
            boolean first = quotaService.reserve(userId, 5);
            boolean second = quotaService.reserve(userId, 5);
            return first && second;
        });

        assertTrue(result != null && result, "Both reservations inside transaction should succeed without transaction abort");
        assertEquals(2, quotaService.getUsageToday(userId));
    }
}
