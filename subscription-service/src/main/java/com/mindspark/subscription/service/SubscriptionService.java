package com.mindspark.subscription.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mindspark.subscription.dto.BillingRecordResponse;
import com.mindspark.subscription.kafka.NoOpSubscriptionKafkaProducer;
import com.mindspark.subscription.kafka.SubscriptionKafkaProducer;
import com.mindspark.subscription.dto.CheckoutConfirmResponse;
import com.mindspark.subscription.dto.CheckoutResponse;
import com.mindspark.subscription.dto.EntitlementResponse;
import com.mindspark.subscription.dto.PaymentConfigResponse;
import com.mindspark.subscription.dto.PlanResponse;
import com.mindspark.subscription.dto.SubscriptionMeResponse;
import com.mindspark.subscription.entity.BillingEventType;
import com.mindspark.subscription.entity.BillingRecord;
import com.mindspark.subscription.entity.BillingRecordStatus;
import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.entity.Subscription;
import com.mindspark.subscription.entity.SubscriptionStatus;
import com.mindspark.subscription.exception.AlreadyOnPlanException;
import com.mindspark.subscription.exception.AlreadySubscribedException;
import com.mindspark.subscription.exception.DowngradeNotSupportedException;
import com.mindspark.subscription.exception.InvalidPlanTransitionException;
import com.mindspark.subscription.exception.NoActiveSubscriptionException;
import com.mindspark.subscription.exception.PlanNotFoundException;
import com.mindspark.subscription.polar.client.PolarCheckoutClient;
import com.mindspark.subscription.polar.config.PolarProperties;
import com.mindspark.subscription.polar.dto.PolarCheckoutDetails;
import com.mindspark.subscription.polar.dto.PolarCheckoutResponse;
import com.mindspark.subscription.polar.exception.CheckoutOwnershipMismatchException;
import com.mindspark.subscription.polar.exception.InvalidReturnOriginException;
import com.mindspark.subscription.polar.exception.PaymentNotCompletedException;
import com.mindspark.subscription.polar.exception.PolarUnavailableException;
import com.mindspark.subscription.polar.exception.UnknownPolarProductException;
import com.mindspark.subscription.repository.BillingRecordRepository;
import com.mindspark.subscription.repository.PlanRepository;
import com.mindspark.subscription.repository.SubscriptionRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubscriptionService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final KeycloakRoleSyncService keycloakRoleSyncService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final PolarProperties polarProperties;
    private final PolarCheckoutClient polarCheckoutClient;
    private final String allowedOriginsConfig;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Object> inMemoryUserLocks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SubscriptionKafkaProducer subscriptionKafkaProducer = new NoOpSubscriptionKafkaProducer();

    public void setSubscriptionKafkaProducer(SubscriptionKafkaProducer subscriptionKafkaProducer) {
        this.subscriptionKafkaProducer = subscriptionKafkaProducer != null ? subscriptionKafkaProducer : new NoOpSubscriptionKafkaProducer();
    }

    @Autowired
    public SubscriptionService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            BillingRecordRepository billingRecordRepository,
            KeycloakRoleSyncService keycloakRoleSyncService,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            PolarProperties polarProperties,
            PolarCheckoutClient polarCheckoutClient,
            @Value("${mindspark.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOriginsConfig) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.keycloakRoleSyncService = keycloakRoleSyncService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.polarProperties = polarProperties;
        this.polarCheckoutClient = polarCheckoutClient;
        this.allowedOriginsConfig = allowedOriginsConfig;
    }

    public SubscriptionService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            BillingRecordRepository billingRecordRepository,
            KeycloakRoleSyncService keycloakRoleSyncService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this(planRepository, subscriptionRepository, billingRecordRepository, keycloakRoleSyncService,
                jdbcTemplate, clock, new PolarProperties(), null, "http://localhost:4200");
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionMeResponse getEffectiveSubscription(String userId) {
        Optional<Subscription> activeSub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (activeSub.isPresent()) {
            return toSubscriptionMeResponse(activeSub.get());
        }

        // Implicit FREE resolution
        Plan freePlan = getFreePlan();
        return new SubscriptionMeResponse(
                freePlan.getCode(),
                freePlan.getName(),
                "ACTIVE",
                null,
                null,
                null,
                null,
                freePlan.getMaxActiveRooms(),
                freePlan.getMaxParticipantsPerRoom(),
                freePlan.getAiDailyLimit(),
                freePlan.getAiTier()
        );
    }

    @Transactional(readOnly = true)
    public EntitlementResponse getEffectiveEntitlement(String userId) {
        Optional<Subscription> activeSub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (activeSub.isPresent()) {
            Plan plan = activeSub.get().getPlan();
            return new EntitlementResponse(
                    userId,
                    plan.getCode(),
                    plan.getName(),
                    plan.getAiTier(),
                    plan.getMaxActiveRooms(),
                    plan.getMaxParticipantsPerRoom(),
                    plan.getAiDailyLimit()
            );
        }

        Plan freePlan = getFreePlan();
        return new EntitlementResponse(
                userId,
                freePlan.getCode(),
                freePlan.getName(),
                freePlan.getAiTier(),
                freePlan.getMaxActiveRooms(),
                freePlan.getMaxParticipantsPerRoom(),
                freePlan.getAiDailyLimit()
        );
    }

    @Transactional
    public SubscriptionMeResponse subscribe(String userId, String planCode, String paymentMethodLabel) {
        acquireUserLock(userId);
        synchronized (getInMemoryLock(userId)) {
            if (planCode == null || planCode.trim().equalsIgnoreCase("FREE")) {
                throw new InvalidPlanTransitionException("Cannot subscribe to FREE plan. FREE plan is default.");
            }

            Plan targetPlan = planRepository.findByCode(planCode.trim().toUpperCase())
                    .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planCode));

            if (!targetPlan.isActive()) {
                throw new PlanNotFoundException("Plan is currently inactive: " + planCode);
            }

            Optional<Subscription> existingActive = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (existingActive.isPresent()) {
                throw new AlreadySubscribedException("User already has an active subscription. Use upgrade to switch plans.");
            }

            Instant now = Instant.now(clock);
            Instant periodEnd = now.plus(30, ChronoUnit.DAYS);

            Subscription subscription = Subscription.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .plan(targetPlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(now)
                    .currentPeriodStart(now)
                    .currentPeriodEnd(periodEnd)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            subscription = subscriptionRepository.save(subscription);

            String paymentSummary = (paymentMethodLabel != null && !paymentMethodLabel.isBlank())
                    ? paymentMethodLabel.trim()
                    : "Simulated Card •••• 4242";

            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(userId)
                    .subscriptionId(subscription.getId())
                    .eventType(BillingEventType.SUBSCRIBE)
                    .planCode(targetPlan.getCode())
                    .planName(targetPlan.getName())
                    .amountCents(targetPlan.getPriceCents())
                    .currency(targetPlan.getCurrency())
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary(paymentSummary)
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            // Sync realm role in Keycloak
            if ("PREMIUM".equalsIgnoreCase(targetPlan.getAiTier())) {
                keycloakRoleSyncService.assignPremiumRole(userId);
            }

            return toSubscriptionMeResponse(subscription);
        }
    }

    @Transactional
    public SubscriptionMeResponse upgrade(String userId, String targetPlanCode, String paymentMethodLabel) {
        acquireUserLock(userId);
        synchronized (getInMemoryLock(userId)) {
            if (targetPlanCode == null || targetPlanCode.trim().equalsIgnoreCase("FREE")) {
                throw new DowngradeNotSupportedException("Downgrade to FREE is not supported via upgrade. Use cancel instead.");
            }

            Plan targetPlan = planRepository.findByCode(targetPlanCode.trim().toUpperCase())
                    .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + targetPlanCode));

            if (!targetPlan.isActive()) {
                throw new PlanNotFoundException("Plan is currently inactive: " + targetPlanCode);
            }

            Optional<Subscription> existingActive = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            int currentPriceCents = 0;
            String currentCode = "FREE";

            if (existingActive.isPresent()) {
                Plan currentPlan = existingActive.get().getPlan();
                currentCode = currentPlan.getCode();
                currentPriceCents = currentPlan.getPriceCents();
            }

            if (targetPlan.getCode().equalsIgnoreCase(currentCode)) {
                throw new AlreadyOnPlanException("User is already on plan: " + targetPlan.getCode());
            }

            if (targetPlan.getPriceCents() < currentPriceCents) {
                throw new DowngradeNotSupportedException("Downgrade not supported. Please cancel your subscription to return to Free.");
            }

            Instant now = Instant.now(clock);
            Instant periodEnd = now.plus(30, ChronoUnit.DAYS);

            // Close existing subscription if present
            if (existingActive.isPresent()) {
                Subscription oldSub = existingActive.get();
                oldSub.setStatus(SubscriptionStatus.EXPIRED);
                oldSub.setUpdatedAt(now);
                subscriptionRepository.save(oldSub);
            }

            // Create new active subscription
            Subscription newSubscription = Subscription.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .plan(targetPlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(existingActive.map(Subscription::getStartedAt).orElse(now))
                    .currentPeriodStart(now)
                    .currentPeriodEnd(periodEnd)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            newSubscription = subscriptionRepository.save(newSubscription);

            int upgradeAmount = Math.max(0, targetPlan.getPriceCents() - currentPriceCents);
            String paymentSummary = (paymentMethodLabel != null && !paymentMethodLabel.isBlank())
                    ? paymentMethodLabel.trim()
                    : "Simulated Card •••• 4242";

            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(userId)
                    .subscriptionId(newSubscription.getId())
                    .eventType(BillingEventType.UPGRADE)
                    .planCode(targetPlan.getCode())
                    .planName(targetPlan.getName())
                    .amountCents(upgradeAmount)
                    .currency(targetPlan.getCurrency())
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary(paymentSummary)
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            if ("PREMIUM".equalsIgnoreCase(targetPlan.getAiTier())) {
                keycloakRoleSyncService.assignPremiumRole(userId);
            }

            subscriptionKafkaProducer.publishSubscriptionChanged(
                    userId,
                    targetPlan.getCode(),
                    currentCode,
                    "ACTIVE",
                    "UPGRADE",
                    "Subscription Upgraded",
                    "Your subscription has been updated to " + targetPlan.getName() + " (" + targetPlan.getCode() + ")."
            );

            return toSubscriptionMeResponse(newSubscription);
        }
    }

    @Transactional
    public SubscriptionMeResponse cancel(String userId) {
        acquireUserLock(userId);
        synchronized (getInMemoryLock(userId)) {
            Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new NoActiveSubscriptionException("No active paid subscription found to cancel."));

            Instant now = Instant.now(clock);
            activeSub.setStatus(SubscriptionStatus.CANCELED);
            activeSub.setCanceledAt(now);
            activeSub.setUpdatedAt(now);
            subscriptionRepository.save(activeSub);

            // Record cancellation invoice (amount 0)
            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(userId)
                    .subscriptionId(activeSub.getId())
                    .eventType(BillingEventType.CANCEL)
                    .planCode(activeSub.getPlan().getCode())
                    .planName(activeSub.getPlan().getName())
                    .amountCents(0)
                    .currency(activeSub.getPlan().getCurrency())
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary("N/A - Subscription Canceled")
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            // Remove premium role in Keycloak
            keycloakRoleSyncService.removePremiumRole(userId);

            subscriptionKafkaProducer.publishSubscriptionChanged(
                    userId,
                    "FREE",
                    activeSub.getPlan().getCode(),
                    "CANCELED",
                    "CANCEL",
                    "Subscription Canceled",
                    "Your subscription has been canceled. Your account is now on the Free plan."
            );

            // Returns effective FREE status
            Plan freePlan = getFreePlan();
            return new SubscriptionMeResponse(
                    freePlan.getCode(),
                    freePlan.getName(),
                    "ACTIVE",
                    null,
                    null,
                    null,
                    now,
                    freePlan.getMaxActiveRooms(),
                    freePlan.getMaxParticipantsPerRoom(),
                    freePlan.getAiDailyLimit(),
                    freePlan.getAiTier()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<BillingRecordResponse> getBillingHistory(String userId) {
        return billingRecordRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toBillingRecordResponse)
                .toList();
    }

    public PaymentConfigResponse getPaymentConfig() {
        boolean enabled = polarProperties.isPolarEnabled();
        String mode = enabled ? "POLAR_SANDBOX" : "SIMULATED";
        String provider = enabled ? "POLAR" : "SIMULATED";
        return new PaymentConfigResponse(mode, provider, List.of("PREMIUM", "TEAM"));
    }

    public String validateReturnOrigin(String requestedOrigin) {
        List<String> allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (requestedOrigin == null || requestedOrigin.isBlank()) {
            return allowedOrigins.isEmpty() ? "http://localhost:4200" : allowedOrigins.get(0);
        }
        String cleanOrigin = requestedOrigin.trim();
        if (cleanOrigin.endsWith("/")) {
            cleanOrigin = cleanOrigin.substring(0, cleanOrigin.length() - 1);
        }
        for (String allowed : allowedOrigins) {
            String cleanAllowed = allowed.endsWith("/") ? allowed.substring(0, allowed.length() - 1) : allowed;
            if (cleanAllowed.equalsIgnoreCase(cleanOrigin)) {
                return cleanOrigin;
            }
        }
        throw new InvalidReturnOriginException("Return origin is not allowed: " + requestedOrigin);
    }

    public CheckoutResponse createCheckout(String userId, String userEmail, String planCode, String returnOrigin) {
        if (planCode == null || planCode.trim().equalsIgnoreCase("FREE")) {
            throw new InvalidPlanTransitionException("Cannot checkout for FREE plan. FREE plan is default.");
        }

        Plan targetPlan = planRepository.findByCode(planCode.trim().toUpperCase())
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planCode));

        if (!targetPlan.isActive()) {
            throw new PlanNotFoundException("Plan is currently inactive: " + planCode);
        }

        Optional<Subscription> existingActive = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (existingActive.isPresent() && existingActive.get().getPlan().getCode().equalsIgnoreCase(targetPlan.getCode())) {
            throw new AlreadyOnPlanException("User is already on plan: " + targetPlan.getCode());
        }

        String validatedOrigin = validateReturnOrigin(returnOrigin);
        String productId = polarProperties.resolveProductId(targetPlan.getCode());

        if (!polarProperties.isPolarEnabled() || productId == null) {
            log.info("Polar not configured or productId missing for {}. Returning simulated mode.", targetPlan.getCode());
            return new CheckoutResponse("SIMULATED", "SIMULATED", null, null, targetPlan.getCode(),
                    "Polar sandbox not configured; use simulated upgrade.");
        }

        String successUrl = validatedOrigin + "/billing?provider=polar&checkout_id={CHECKOUT_ID}";
        String returnUrl = validatedOrigin + "/billing?provider=polar&canceled=true";

        PolarCheckoutResponse polarCheckout = polarCheckoutClient.createCheckout(
                productId, successUrl, returnUrl, userId, userEmail, targetPlan.getCode());
        if (polarCheckout == null) {
            throw new PolarUnavailableException("Payment provider returned empty checkout response");
        }

        return new CheckoutResponse(
                "POLAR_SANDBOX",
                "POLAR",
                polarCheckout.id(),
                polarCheckout.url(),
                targetPlan.getCode(),
                null
        );
    }

    public CheckoutConfirmResponse confirmCheckout(String userId, String checkoutId) {
        // Fast-path: Check existing billing record idempotency
        Optional<BillingRecord> existingRecordOpt = billingRecordRepository.findByExternalCheckoutId(checkoutId);
        if (existingRecordOpt.isPresent()) {
            BillingRecord existingRecord = existingRecordOpt.get();
            if (!existingRecord.getUserId().equals(userId)) {
                throw new CheckoutOwnershipMismatchException("Checkout belongs to a different user");
            }
            SubscriptionMeResponse currentSub = getEffectiveSubscription(userId);
            return new CheckoutConfirmResponse(
                    "ACTIVATED",
                    true,
                    currentSub,
                    existingRecord.getId().toString(),
                    checkoutId,
                    null,
                    "Checkout already processed successfully"
            );
        }

        // Fetch Polar checkout details outside DB transaction
        PolarCheckoutDetails checkoutDetails = polarCheckoutClient.getCheckout(checkoutId);
        if (checkoutDetails == null) {
            throw new PolarUnavailableException("Payment provider returned empty checkout details");
        }

        // Security check: Ownership
        String polarUserId = checkoutDetails.externalCustomerId();
        if (polarUserId == null && checkoutDetails.metadata() != null) {
            Object uidMeta = checkoutDetails.metadata().get("user_id");
            if (uidMeta != null) {
                polarUserId = uidMeta.toString();
            }
        }
        if (polarUserId == null || !polarUserId.equals(userId)) {
            throw new CheckoutOwnershipMismatchException(
                    "Checkout ownership mismatch: expected user " + userId + ", got " + polarUserId);
        }

        // Map Product ID to Plan
        String resolvedPlanCode = polarProperties.resolvePlanCode(checkoutDetails.productId());
        if (resolvedPlanCode == null && checkoutDetails.metadata() != null) {
            Object pcMeta = checkoutDetails.metadata().get("plan_code");
            if (pcMeta != null) {
                String candidate = pcMeta.toString().toUpperCase();
                String expectedPid = polarProperties.resolveProductId(candidate);
                if (expectedPid != null && (checkoutDetails.productId() == null || expectedPid.equals(checkoutDetails.productId()))) {
                    resolvedPlanCode = candidate;
                }
            }
        }
        if (resolvedPlanCode == null) {
            throw new UnknownPolarProductException("Unknown Polar product ID: " + checkoutDetails.productId());
        }

        final String finalPlanCode = resolvedPlanCode;
        Plan targetPlan = planRepository.findByCode(finalPlanCode)
                .orElseThrow(() -> new UnknownPolarProductException("Unknown plan code: " + finalPlanCode));

        // Check status
        String status = checkoutDetails.status() != null ? checkoutDetails.status().toLowerCase() : "";
        if ("confirmed".equals(status)) {
            return new CheckoutConfirmResponse(
                    "PENDING_CONFIRMATION",
                    false,
                    null,
                    null,
                    checkoutId,
                    1500L,
                    "Payment confirmed by Polar and finalizing..."
            );
        }
        if (!"succeeded".equals(status)) {
            throw new PaymentNotCompletedException("Payment not completed in Polar (status: " + status + ")", status);
        }

        // Transactional activation inside lock
        return activatePolarSubscription(userId, targetPlan, checkoutId, checkoutDetails);
    }

    @Transactional
    public CheckoutConfirmResponse activatePolarSubscription(
            String userId, Plan targetPlan, String checkoutId, PolarCheckoutDetails checkoutDetails) {
        acquireUserLock(userId);
        synchronized (getInMemoryLock(userId)) {
            // Double-check idempotency inside lock
            Optional<BillingRecord> existingRecordOpt = billingRecordRepository.findByExternalCheckoutId(checkoutId);
            if (existingRecordOpt.isPresent()) {
                BillingRecord existingRecord = existingRecordOpt.get();
                if (!existingRecord.getUserId().equals(userId)) {
                    throw new CheckoutOwnershipMismatchException("Checkout belongs to a different user");
                }
                return new CheckoutConfirmResponse(
                        "ACTIVATED",
                        true,
                        getEffectiveSubscription(userId),
                        existingRecord.getId().toString(),
                        checkoutId,
                        null,
                        "Checkout already processed successfully"
                );
            }

            Optional<Subscription> existingActive = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            Instant now = Instant.now(clock);
            Instant periodEnd = now.plus(30, ChronoUnit.DAYS);

            // Close existing active subscription if present
            if (existingActive.isPresent()) {
                Subscription oldSub = existingActive.get();
                oldSub.setStatus(SubscriptionStatus.EXPIRED);
                oldSub.setUpdatedAt(now);
                subscriptionRepository.save(oldSub);
            }

            // Create new active subscription
            Subscription newSubscription = Subscription.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .plan(targetPlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(existingActive.map(Subscription::getStartedAt).orElse(now))
                    .currentPeriodStart(now)
                    .currentPeriodEnd(periodEnd)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            newSubscription = subscriptionRepository.save(newSubscription);

            int amount = checkoutDetails.amount() > 0 ? checkoutDetails.amount() : targetPlan.getPriceCents();
            String currency = checkoutDetails.currency() != null ? checkoutDetails.currency().toUpperCase() : targetPlan.getCurrency();

            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(userId)
                    .subscriptionId(newSubscription.getId())
                    .eventType(existingActive.isPresent() ? BillingEventType.UPGRADE : BillingEventType.SUBSCRIBE)
                    .planCode(targetPlan.getCode())
                    .planName(targetPlan.getName())
                    .amountCents(amount)
                    .currency(currency)
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary("Polar Sandbox •••• 4242")
                    .externalCheckoutId(checkoutId)
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            // Sync realm role in Keycloak
            if ("PREMIUM".equalsIgnoreCase(targetPlan.getAiTier())) {
                keycloakRoleSyncService.assignPremiumRole(userId);
            }

            String previousCode = existingActive.isPresent() ? existingActive.get().getPlan().getCode() : "FREE";
            subscriptionKafkaProducer.publishSubscriptionChanged(
                    userId,
                    targetPlan.getCode(),
                    previousCode,
                    "ACTIVE",
                    "POLAR_CHECKOUT",
                    "Subscription Activated",
                    "Your payment succeeded and your " + targetPlan.getName() + " plan is now active."
            );

            log.info("Activated Polar subscription for userId={}, plan={}, checkoutId={}", userId, targetPlan.getCode(), checkoutId);

            return new CheckoutConfirmResponse(
                    "ACTIVATED",
                    false,
                    toSubscriptionMeResponse(newSubscription),
                    record.getId().toString(),
                    checkoutId,
                    null,
                    "Subscription activated successfully"
            );
        }
    }

    private void acquireUserLock(String userId) {
        try {
            String dbProductName = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<String>) conn ->
                    conn.getMetaData().getDatabaseProductName()
            );
            if (dbProductName != null && dbProductName.toLowerCase().contains("postgres")) {
                jdbcTemplate.queryForObject(
                        "SELECT pg_advisory_xact_lock(hashtext(?))",
                        Object.class,
                        "sub:" + userId
                );
            }
        } catch (Exception e) {
            log.debug("Advisory lock not applicable or failed (likely H2/test): {}", e.getMessage());
        }
    }

    private Object getInMemoryLock(String userId) {
        return inMemoryUserLocks.computeIfAbsent(userId, k -> new Object());
    }

    private String generateInvoiceNumber() {
        int year = ZonedDateTime.now(clock).getYear();
        int randomPart = Math.abs(random.nextInt(900000) + 100000);
        return String.format("INV-%d-%06d", year, randomPart);
    }

    private Plan getFreePlan() {
        return planRepository.findByCode("FREE")
                .orElseGet(() -> Plan.builder()
                        .code("FREE")
                        .name("Free")
                        .priceCents(0)
                        .currency("USD")
                        .billingInterval("MONTHLY")
                        .maxActiveRooms(3)
                        .maxParticipantsPerRoom(5)
                        .aiDailyLimit(10)
                        .aiTier("FREE")
                        .active(true)
                        .sortOrder(1)
                        .build());
    }

    private PlanResponse toPlanResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getMaxActiveRooms(),
                plan.getMaxParticipantsPerRoom(),
                plan.getAiDailyLimit(),
                plan.getAiTier(),
                plan.getSortOrder()
        );
    }

    private SubscriptionMeResponse toSubscriptionMeResponse(Subscription sub) {
        Plan plan = sub.getPlan();
        return new SubscriptionMeResponse(
                plan.getCode(),
                plan.getName(),
                sub.getStatus().name(),
                sub.getStartedAt(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getCanceledAt(),
                plan.getMaxActiveRooms(),
                plan.getMaxParticipantsPerRoom(),
                plan.getAiDailyLimit(),
                plan.getAiTier()
        );
    }

    private BillingRecordResponse toBillingRecordResponse(BillingRecord record) {
        return new BillingRecordResponse(
                record.getId(),
                record.getInvoiceNumber(),
                record.getEventType().name(),
                record.getPlanCode(),
                record.getPlanName(),
                record.getAmountCents(),
                record.getCurrency(),
                record.getStatus().name(),
                record.getPaymentMethodSummary(),
                record.getCreatedAt()
        );
    }
}
