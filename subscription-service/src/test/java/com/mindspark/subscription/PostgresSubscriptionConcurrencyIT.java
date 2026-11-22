package com.mindspark.subscription;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@EnabledIfSystemProperty(named = "run.postgres.sub.it", matches = "true")
class PostgresSubscriptionConcurrencyIT {

    private static JdbcTemplate jdbcTemplate;
    private static UUID samplePlanId;

    @BeforeAll
    static void initDataSource() {
        String host = System.getProperty("test.postgres.host", "localhost");
        String port = System.getProperty("test.postgres.port", "5435");
        String dbName = System.getProperty("test.postgres.db", "sub_it_temp");
        String user = System.getProperty("test.postgres.user", "subscription_user");
        String password = System.getProperty("test.postgres.password", "subscription_dev_pass");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        dataSource.setUsername(user);
        dataSource.setPassword(password);

        jdbcTemplate = new JdbcTemplate(dataSource);

        // Create schema
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS plans (
                id UUID PRIMARY KEY,
                code VARCHAR(32) NOT NULL UNIQUE,
                name VARCHAR(100) NOT NULL,
                price_cents INT NOT NULL,
                currency VARCHAR(3) NOT NULL,
                billing_interval VARCHAR(20) NOT NULL,
                max_active_rooms INT NOT NULL,
                max_participants_per_room INT NOT NULL,
                ai_daily_limit INT NOT NULL,
                ai_tier VARCHAR(20) NOT NULL,
                active BOOLEAN NOT NULL DEFAULT TRUE,
                sort_order INT NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL
            );

            CREATE TABLE IF NOT EXISTS subscriptions (
                id UUID PRIMARY KEY,
                user_id VARCHAR(255) NOT NULL,
                plan_id UUID NOT NULL REFERENCES plans(id),
                status VARCHAR(20) NOT NULL,
                started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
                current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
                canceled_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL
            );

            CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_user_active
                ON subscriptions (user_id) WHERE status = 'ACTIVE';
        """);

        samplePlanId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO plans (id, code, name, price_cents, currency, billing_interval, max_active_rooms, max_participants_per_room, ai_daily_limit, ai_tier, active, sort_order, created_at, updated_at)
            VALUES (?, 'PREMIUM', 'Premium', 900, 'USD', 'MONTHLY', 20, 25, 20, 'PREMIUM', true, 2, now(), now())
            ON CONFLICT (code) DO NOTHING
        """, samplePlanId);
    }

    @Test
    @DisplayName("PostgreSQL Partial Unique Index: 20 parallel threads attempting ACTIVE subscription insert yield exactly 1 success")
    void testPostgresPartialUniqueIndexConcurrency() throws InterruptedException {
        String userId = "pg-concurrent-sub-" + System.currentTimeMillis();
        int threadCount = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    jdbcTemplate.update("""
                        INSERT INTO subscriptions (id, user_id, plan_id, status, started_at, current_period_start, current_period_end, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', now(), now(), now() + interval '30 days', now(), now())
                    """, UUID.randomUUID(), userId, samplePlanId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    duplicateCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All 20 threads should complete within 15 seconds");
        assertEquals(1, successCount.get(), "Exactly 1 thread must succeed inserting ACTIVE subscription");
        assertEquals(19, duplicateCount.get(), "19 threads must fail due to partial unique index constraint");

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        assertEquals(1, activeCount);
    }
}
