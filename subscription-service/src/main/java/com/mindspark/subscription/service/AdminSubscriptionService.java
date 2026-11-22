package com.mindspark.subscription.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mindspark.subscription.dto.AdminAssignRequest;
import com.mindspark.subscription.dto.AdminCancelResponse;
import com.mindspark.subscription.dto.AdminPlanResponse;
import com.mindspark.subscription.dto.AdminSubscriptionResponse;
import com.mindspark.subscription.dto.AdminSubscriptionsPageResponse;
import com.mindspark.subscription.dto.BillingRecordResponse;
import com.mindspark.subscription.dto.BillingStatsResponse;
import com.mindspark.subscription.dto.BillingStatsResponse.PlanSubscriberCount;
import com.mindspark.subscription.dto.CreatePlanRequest;
import com.mindspark.subscription.dto.UpdatePlanRequest;
import com.mindspark.subscription.entity.BillingEventType;
import com.mindspark.subscription.entity.BillingRecord;
import com.mindspark.subscription.entity.BillingRecordStatus;
import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.entity.Subscription;
import com.mindspark.subscription.entity.SubscriptionStatus;
import com.mindspark.subscription.exception.InvalidPlanUpdateException;
import com.mindspark.subscription.exception.NoActiveSubscriptionException;
import com.mindspark.subscription.exception.PlanCodeExistsException;
import com.mindspark.subscription.exception.PlanNotFoundException;
import com.mindspark.subscription.repository.BillingRecordRepository;
import com.mindspark.subscription.repository.PlanRepository;
import com.mindspark.subscription.repository.SubscriptionRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminSubscriptionService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final KeycloakRoleSyncService keycloakRoleSyncService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Object> inMemoryUserLocks = new ConcurrentHashMap<>();

    public AdminSubscriptionService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            BillingRecordRepository billingRecordRepository,
            KeycloakRoleSyncService keycloakRoleSyncService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.keycloakRoleSyncService = keycloakRoleSyncService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // ==========================================
    // PLANS MANAGEMENT (Story 63)
    // ==========================================

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> getAllPlans() {
        List<Plan> plans = planRepository.findAllByOrderBySortOrderAsc();
        return plans.stream().map(this::toAdminPlanResponse).toList();
    }

    @Transactional
    public AdminPlanResponse createPlan(CreatePlanRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (planRepository.findByCode(code).isPresent()) {
            throw new PlanCodeExistsException("Plan code already exists: " + code);
        }

        Instant now = Instant.now(clock);
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(req.name().trim())
                .description(req.description() != null ? req.description().trim() : "")
                .priceCents(req.priceCents())
                .currency(req.currency().trim().toUpperCase(Locale.ROOT))
                .billingInterval(req.billingInterval().trim().toUpperCase(Locale.ROOT))
                .maxActiveRooms(req.maxActiveRooms())
                .maxParticipantsPerRoom(req.maxParticipantsPerRoom())
                .aiDailyLimit(req.aiDailyLimit())
                .aiTier(req.aiTier().trim().toUpperCase(Locale.ROOT))
                .active(true)
                .sortOrder(req.sortOrder())
                .createdAt(now)
                .updatedAt(now)
                .build();

        plan = planRepository.save(plan);
        return toAdminPlanResponse(plan);
    }

    @Transactional
    public AdminPlanResponse updatePlan(UUID id, UpdatePlanRequest req) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + id));

        // Critical invariants for FREE plan
        if ("FREE".equalsIgnoreCase(plan.getCode())) {
            if (!req.active()) {
                throw new InvalidPlanUpdateException("FREE plan cannot be deactivated.");
            }
            if (req.priceCents() > 0) {
                throw new InvalidPlanUpdateException("FREE plan price must remain 0.");
            }
        }

        Instant now = Instant.now(clock);
        plan.setName(req.name().trim());
        plan.setDescription(req.description() != null ? req.description().trim() : "");
        plan.setPriceCents(req.priceCents());
        plan.setCurrency(req.currency().trim().toUpperCase(Locale.ROOT));
        plan.setBillingInterval(req.billingInterval().trim().toUpperCase(Locale.ROOT));
        plan.setMaxActiveRooms(req.maxActiveRooms());
        plan.setMaxParticipantsPerRoom(req.maxParticipantsPerRoom());
        plan.setAiDailyLimit(req.aiDailyLimit());
        plan.setAiTier(req.aiTier().trim().toUpperCase(Locale.ROOT));
        plan.setActive(req.active());
        plan.setSortOrder(req.sortOrder());
        plan.setUpdatedAt(now);

        plan = planRepository.save(plan);
        return toAdminPlanResponse(plan);
    }

    @Transactional
    public AdminPlanResponse togglePlanStatus(UUID id, Boolean active) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + id));

        if ("FREE".equalsIgnoreCase(plan.getCode())) {
            throw new InvalidPlanUpdateException("FREE plan cannot be deactivated.");
        }

        boolean nextStatus = (active != null) ? active : !plan.isActive();
        plan.setActive(nextStatus);
        plan.setUpdatedAt(Instant.now(clock));

        plan = planRepository.save(plan);
        return toAdminPlanResponse(plan);
    }

    // ==========================================
    // SUBSCRIPTIONS MANAGEMENT (Story 62)
    // ==========================================

    @Transactional(readOnly = true)
    public AdminSubscriptionsPageResponse getSubscriptions(String status, String planCode, String userId, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, 100));
        int pageIndex = Math.max(0, page);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Subscription> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try {
                    SubscriptionStatus subStatus = SubscriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
                    predicates.add(cb.equal(root.get("status"), subStatus));
                } catch (IllegalArgumentException ignored) {}
            }
            if (planCode != null && !planCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("plan").get("code")), planCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (userId != null && !userId.isBlank()) {
                predicates.add(cb.equal(root.get("userId"), userId.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Subscription> subPage = subscriptionRepository.findAll(spec, pageable);
        List<AdminSubscriptionResponse> items = subPage.getContent().stream()
                .map(this::toAdminSubscriptionResponse)
                .toList();

        return new AdminSubscriptionsPageResponse(
                items,
                subPage.getNumber(),
                subPage.getSize(),
                subPage.getTotalElements(),
                subPage.getTotalPages()
        );
    }

    @Transactional
    public AdminSubscriptionResponse adminAssignPlan(AdminAssignRequest req, String adminActor) {
        String userId = req.userId().trim();
        String targetCode = req.planCode().trim().toUpperCase(Locale.ROOT);
        String reason = req.reason() != null ? req.reason().trim() : "";

        acquireUserLock(userId);
        synchronized (getInMemoryLock(userId)) {
            Instant now = Instant.now(clock);
            String actor = (adminActor != null && !adminActor.isBlank()) ? adminActor : "admin";
            String paymentSummary = "Admin Override: " + actor + (reason.isEmpty() ? "" : " (" + reason + ")");

            if ("FREE".equalsIgnoreCase(targetCode)) {
                // If user has an active subscription, cancel it
                Optional<Subscription> activeOpt = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
                Subscription canceledSub;
                if (activeOpt.isPresent()) {
                    canceledSub = activeOpt.get();
                    canceledSub.setStatus(SubscriptionStatus.CANCELED);
                    canceledSub.setCanceledAt(now);
                    canceledSub.setUpdatedAt(now);
                    canceledSub = subscriptionRepository.save(canceledSub);

                    // Record $0 ADMIN_CANCEL invoice
                    BillingRecord record = BillingRecord.builder()
                            .id(UUID.randomUUID())
                            .invoiceNumber(generateInvoiceNumber())
                            .userId(userId)
                            .subscriptionId(canceledSub.getId())
                            .eventType(BillingEventType.ADMIN_CANCEL)
                            .planCode(canceledSub.getPlan().getCode())
                            .planName(canceledSub.getPlan().getName())
                            .amountCents(0)
                            .currency(canceledSub.getPlan().getCurrency())
                            .status(BillingRecordStatus.PAID)
                            .paymentMethodSummary(paymentSummary)
                            .createdAt(now)
                            .build();
                    billingRecordRepository.save(record);
                } else {
                    // Implicit FREE user with no row
                    Plan freePlan = getFreePlan();
                    canceledSub = Subscription.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .plan(freePlan)
                            .status(SubscriptionStatus.ACTIVE)
                            .startedAt(now)
                            .currentPeriodStart(now)
                            .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                }

                // Remove premium role
                keycloakRoleSyncService.removePremiumRole(userId);
                return toAdminSubscriptionResponse(canceledSub);
            }

            // Target paid plan
            Plan targetPlan = planRepository.findByCode(targetCode)
                    .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + targetCode));

            // Close existing subscription if any
            Optional<Subscription> activeOpt = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (activeOpt.isPresent()) {
                Subscription oldSub = activeOpt.get();
                oldSub.setStatus(SubscriptionStatus.EXPIRED);
                oldSub.setUpdatedAt(now);
                subscriptionRepository.save(oldSub);
            }

            Instant periodEnd = now.plus(30, ChronoUnit.DAYS);
            Subscription newSub = Subscription.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .plan(targetPlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(activeOpt.map(Subscription::getStartedAt).orElse(now))
                    .currentPeriodStart(now)
                    .currentPeriodEnd(periodEnd)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            newSub = subscriptionRepository.save(newSub);

            // Record $0 ADMIN_ASSIGN invoice
            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(userId)
                    .subscriptionId(newSub.getId())
                    .eventType(BillingEventType.ADMIN_ASSIGN)
                    .planCode(targetPlan.getCode())
                    .planName(targetPlan.getName())
                    .amountCents(0)
                    .currency(targetPlan.getCurrency())
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary(paymentSummary)
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            // Sync Keycloak role
            if ("PREMIUM".equalsIgnoreCase(targetPlan.getAiTier())) {
                keycloakRoleSyncService.assignPremiumRole(userId);
            } else {
                keycloakRoleSyncService.removePremiumRole(userId);
            }

            return toAdminSubscriptionResponse(newSub);
        }
    }

    @Transactional
    public AdminCancelResponse adminCancelSubscription(String userId, String adminActor) {
        String cleanUserId = userId.trim();
        acquireUserLock(cleanUserId);
        synchronized (getInMemoryLock(cleanUserId)) {
            Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(cleanUserId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new NoActiveSubscriptionException("No active paid subscription found for user: " + cleanUserId));

            Instant now = Instant.now(clock);
            activeSub.setStatus(SubscriptionStatus.CANCELED);
            activeSub.setCanceledAt(now);
            activeSub.setUpdatedAt(now);
            activeSub = subscriptionRepository.save(activeSub);

            String actor = (adminActor != null && !adminActor.isBlank()) ? adminActor : "admin";
            String paymentSummary = "Admin Cancellation: " + actor;

            // Record $0 ADMIN_CANCEL invoice
            BillingRecord record = BillingRecord.builder()
                    .id(UUID.randomUUID())
                    .invoiceNumber(generateInvoiceNumber())
                    .userId(cleanUserId)
                    .subscriptionId(activeSub.getId())
                    .eventType(BillingEventType.ADMIN_CANCEL)
                    .planCode(activeSub.getPlan().getCode())
                    .planName(activeSub.getPlan().getName())
                    .amountCents(0)
                    .currency(activeSub.getPlan().getCurrency())
                    .status(BillingRecordStatus.PAID)
                    .paymentMethodSummary(paymentSummary)
                    .createdAt(now)
                    .build();
            billingRecordRepository.save(record);

            KeycloakRoleSyncService.RoleSyncStatus syncStatus = keycloakRoleSyncService.removePremiumRole(cleanUserId);
            return new AdminCancelResponse(toAdminSubscriptionResponse(activeSub), syncStatus.name());
        }
    }

    // ==========================================
    // BILLING STATS (Story 64)
    // ==========================================

    @Transactional(readOnly = true)
    public BillingStatsResponse getBillingStats() {
        long totalSubs = subscriptionRepository.count();
        long activePaidSubs = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);

        // Subscribers by plan
        List<Plan> plans = planRepository.findAllByOrderBySortOrderAsc();
        List<PlanSubscriberCount> subscribersByPlan = plans.stream()
                .map(p -> new PlanSubscriberCount(
                        p.getCode(),
                        p.getName(),
                        subscriptionRepository.countByPlanIdAndStatus(p.getId(), SubscriptionStatus.ACTIVE)
                ))
                .toList();

        long totalRevenueCents = billingRecordRepository.sumTotalRevenueCents(BillingRecordStatus.PAID);

        // Estimated MRR: sum of current active subscriptions' plan.price_cents
        long estimatedMrrCents = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .mapToLong(s -> s.getPlan().getPriceCents())
                .sum();

        Instant thirtyDaysAgo = Instant.now(clock).minus(30, ChronoUnit.DAYS);
        long recentBillingEventsCount30d = billingRecordRepository.countByCreatedAtAfter(thirtyDaysAgo);

        List<BillingRecordResponse> recentBillingRecords = billingRecordRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::toBillingRecordResponse)
                .toList();

        return new BillingStatsResponse(
                totalSubs,
                activePaidSubs,
                subscribersByPlan,
                totalRevenueCents,
                estimatedMrrCents,
                recentBillingEventsCount30d,
                recentBillingRecords
        );
    }

    // ==========================================
    // HELPERS & MAPPERS
    // ==========================================

    private AdminPlanResponse toAdminPlanResponse(Plan plan) {
        long activeSubs = subscriptionRepository.countByPlanIdAndStatus(plan.getId(), SubscriptionStatus.ACTIVE);
        return new AdminPlanResponse(
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
                plan.isActive(),
                plan.getSortOrder(),
                activeSubs
        );
    }

    private AdminSubscriptionResponse toAdminSubscriptionResponse(Subscription sub) {
        return new AdminSubscriptionResponse(
                sub.getId(),
                sub.getUserId(),
                sub.getPlan() != null ? sub.getPlan().getCode() : "UNKNOWN",
                sub.getPlan() != null ? sub.getPlan().getName() : "Unknown Plan",
                sub.getStatus() != null ? sub.getStatus().name() : "UNKNOWN",
                sub.getStartedAt(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getCanceledAt(),
                sub.getCreatedAt()
        );
    }

    private BillingRecordResponse toBillingRecordResponse(BillingRecord r) {
        return new BillingRecordResponse(
                r.getId(),
                r.getInvoiceNumber(),
                r.getEventType() != null ? r.getEventType().name() : "UNKNOWN",
                r.getPlanCode(),
                r.getPlanName(),
                r.getAmountCents(),
                r.getCurrency(),
                r.getStatus() != null ? r.getStatus().name() : "UNKNOWN",
                r.getPaymentMethodSummary(),
                r.getCreatedAt()
        );
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
}
