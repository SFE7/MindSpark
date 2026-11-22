package com.mindspark.subscription;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mindspark.subscription.dto.BillingRecordResponse;
import com.mindspark.subscription.dto.PlanResponse;
import com.mindspark.subscription.dto.SubscriptionMeResponse;
import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.exception.AlreadyOnPlanException;
import com.mindspark.subscription.exception.AlreadySubscribedException;
import com.mindspark.subscription.exception.DowngradeNotSupportedException;
import com.mindspark.subscription.exception.InvalidPlanTransitionException;
import com.mindspark.subscription.exception.NoActiveSubscriptionException;
import com.mindspark.subscription.exception.PlanNotFoundException;
import com.mindspark.subscription.repository.BillingRecordRepository;
import com.mindspark.subscription.repository.PlanRepository;
import com.mindspark.subscription.repository.SubscriptionRepository;
import com.mindspark.subscription.service.DatabaseInitializer;
import com.mindspark.subscription.service.KeycloakRoleSyncService;
import com.mindspark.subscription.service.SubscriptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sub_lifecycle_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/mindspark",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/mindspark/protocol/openid-connect/certs"
})
class SubscriptionLifecycleAndLimitsTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private KeycloakRoleSyncService keycloakRoleSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseInitializer databaseInitializer;

    private SubscriptionService subscriptionService;
    private MutableClock clock;

    private static class MutableClock extends Clock {
        private Instant currentInstant;

        public MutableClock(Instant initial) {
            this.currentInstant = initial;
        }

        public void advanceSeconds(long seconds) {
            this.currentInstant = this.currentInstant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        billingRecordRepository.deleteAll();
        subscriptionRepository.deleteAll();
        databaseInitializer.run(null);

        clock = new MutableClock(Instant.parse("2026-11-20T12:00:00Z"));
        subscriptionService = new SubscriptionService(
                planRepository,
                subscriptionRepository,
                billingRecordRepository,
                keycloakRoleSyncService,
                jdbcTemplate,
                clock
        );
    }

    @Test
    @DisplayName("Plan seeding is idempotent: re-running initializer does not duplicate or overwrite plans")
    void testPlanSeedingIdempotency() {
        List<PlanResponse> plans = subscriptionService.getActivePlans();
        assertThat(plans).hasSize(3);
        assertThat(plans).extracting(PlanResponse::code).containsExactly("FREE", "PREMIUM", "TEAM");

        // Run again
        databaseInitializer.run(null);
        List<PlanResponse> plansAfter = subscriptionService.getActivePlans();
        assertThat(plansAfter).hasSize(3);
    }

    @Test
    @DisplayName("Implicit FREE resolution: user with no subscription row returns effective FREE limits")
    void testImplicitFreeResolution() {
        String userId = "new-user-no-sub";
        SubscriptionMeResponse me = subscriptionService.getEffectiveSubscription(userId);

        assertThat(me.planCode()).isEqualTo("FREE");
        assertThat(me.planName()).isEqualTo("Free");
        assertThat(me.status()).isEqualTo("ACTIVE");
        assertThat(me.maxActiveRooms()).isEqualTo(3);
        assertThat(me.maxParticipantsPerRoom()).isEqualTo(5);
        assertThat(me.aiDailyLimit()).isEqualTo(10);
        assertThat(me.aiTier()).isEqualTo("FREE");
    }

    @Test
    @DisplayName("Full Lifecycle: Subscribe (PREMIUM) -> Upgrade (TEAM) -> Cancel -> Immutable Invoice Snapshot")
    void testFullSubscriptionLifecycleAndImmutableSnapshot() {
        String userId = "lifecycle-user-1";

        // 1. Subscribe to PREMIUM
        SubscriptionMeResponse subResp = subscriptionService.subscribe(userId, "PREMIUM", "Visa •••• 1234");
        assertThat(subResp.planCode()).isEqualTo("PREMIUM");
        assertThat(subResp.status()).isEqualTo("ACTIVE");
        assertThat(subResp.maxActiveRooms()).isEqualTo(20);
        assertThat(subResp.maxParticipantsPerRoom()).isEqualTo(25);
        assertThat(subResp.aiDailyLimit()).isEqualTo(20);
        assertThat(subResp.aiTier()).isEqualTo("PREMIUM");

        List<BillingRecordResponse> historyAfterSub = subscriptionService.getBillingHistory(userId);
        assertThat(historyAfterSub).hasSize(1);
        BillingRecordResponse subInvoice = historyAfterSub.get(0);
        assertThat(subInvoice.eventType()).isEqualTo("SUBSCRIBE");
        assertThat(subInvoice.planCode()).isEqualTo("PREMIUM");
        assertThat(subInvoice.amountCents()).isEqualTo(900);
        assertThat(subInvoice.status()).isEqualTo("PAID");

        // 2. Upgrade to TEAM
        clock.advanceSeconds(5);
        SubscriptionMeResponse upgradeResp = subscriptionService.upgrade(userId, "TEAM", "Visa •••• 1234");
        assertThat(upgradeResp.planCode()).isEqualTo("TEAM");
        assertThat(upgradeResp.status()).isEqualTo("ACTIVE");
        assertThat(upgradeResp.maxActiveRooms()).isEqualTo(100);
        assertThat(upgradeResp.maxParticipantsPerRoom()).isEqualTo(100);

        List<BillingRecordResponse> historyAfterUpgrade = subscriptionService.getBillingHistory(userId);
        assertThat(historyAfterUpgrade).hasSize(2);
        BillingRecordResponse upgradeInvoice = historyAfterUpgrade.get(0); // newest first
        assertThat(upgradeInvoice.eventType()).isEqualTo("UPGRADE");
        assertThat(upgradeInvoice.planCode()).isEqualTo("TEAM");
        // Prorated difference: 2400 - 900 = 1500 cents ($15.00)
        assertThat(upgradeInvoice.amountCents()).isEqualTo(1500);

        // 3. Mutate Plan price in DB to simulate Admin price change
        Plan premiumPlan = planRepository.findByCode("PREMIUM").orElseThrow();
        premiumPlan.setPriceCents(1200); // Changed from 900 to 1200
        planRepository.save(premiumPlan);

        // Verify past billing records are strictly IMMUTABLE snapshots
        List<BillingRecordResponse> historyAfterPriceChange = subscriptionService.getBillingHistory(userId);
        BillingRecordResponse historicalSubInvoice = historyAfterPriceChange.stream()
                .filter(i -> i.eventType().equals("SUBSCRIBE"))
                .findFirst().orElseThrow();
        assertThat(historicalSubInvoice.amountCents()).isEqualTo(900); // Still 900!

        // 4. Cancel Subscription
        clock.advanceSeconds(5);
        SubscriptionMeResponse cancelResp = subscriptionService.cancel(userId);
        assertThat(cancelResp.planCode()).isEqualTo("FREE");
        assertThat(cancelResp.maxActiveRooms()).isEqualTo(3);

        List<BillingRecordResponse> historyAfterCancel = subscriptionService.getBillingHistory(userId);
        assertThat(historyAfterCancel).hasSize(3);
        BillingRecordResponse cancelInvoice = historyAfterCancel.get(0);
        assertThat(cancelInvoice.eventType()).isEqualTo("CANCEL");
        assertThat(cancelInvoice.amountCents()).isEqualTo(0);
        assertThat(cancelInvoice.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Invalid Transitions: subscribe to FREE, duplicate subscribe, upgrade to lower/same plan, cancel FREE")
    void testInvalidTransitions() {
        String userId = "invalid-transitions-user";

        // 1. Subscribe to FREE -> 400 INVALID_PLAN_TRANSITION
        assertThatThrownBy(() -> subscriptionService.subscribe(userId, "FREE", null))
                .isInstanceOf(InvalidPlanTransitionException.class)
                .hasMessageContaining("Cannot subscribe to FREE");

        // 2. Unknown plan -> 404 PLAN_NOT_FOUND
        assertThatThrownBy(() -> subscriptionService.subscribe(userId, "NON_EXISTENT", null))
                .isInstanceOf(PlanNotFoundException.class);

        // 3. Subscribe to PREMIUM
        subscriptionService.subscribe(userId, "PREMIUM", null);

        // 4. Duplicate subscribe when already active -> 409 ALREADY_SUBSCRIBED
        assertThatThrownBy(() -> subscriptionService.subscribe(userId, "PREMIUM", null))
                .isInstanceOf(AlreadySubscribedException.class)
                .hasMessageContaining("already has an active subscription");

        // 5. Upgrade to same plan -> 409 ALREADY_ON_PLAN
        assertThatThrownBy(() -> subscriptionService.upgrade(userId, "PREMIUM", null))
                .isInstanceOf(AlreadyOnPlanException.class)
                .hasMessageContaining("already on plan");

        // 6. Downgrade to FREE via upgrade -> 400 DOWNGRADE_NOT_SUPPORTED
        assertThatThrownBy(() -> subscriptionService.upgrade(userId, "FREE", null))
                .isInstanceOf(DowngradeNotSupportedException.class);

        // 7. Cancel on user with no active paid subscription -> 409 NO_ACTIVE_SUBSCRIPTION
        String freeUserId = "only-free-user";
        assertThatThrownBy(() -> subscriptionService.cancel(freeUserId))
                .isInstanceOf(NoActiveSubscriptionException.class);
    }

    @Test
    @DisplayName("Concurrent double-subscribe: 10 parallel threads yield exactly 1 active subscription and 1 invoice")
    void testConcurrentDoubleSubscribe() throws InterruptedException {
        String userId = "concurrent-sub-user-" + System.currentTimeMillis();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    subscriptionService.subscribe(userId, "PREMIUM", "Test Card");
                    successCount.incrementAndGet();
                } catch (AlreadySubscribedException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // Any other conflict
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        List<BillingRecordResponse> history = subscriptionService.getBillingHistory(userId);
        assertThat(history).hasSize(1);
    }
}
