package com.mindspark.subscription.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.repository.PlanRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;

    public DatabaseInitializer(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedPlanIfMissing(
                "FREE",
                "Free",
                "Essential collaborative whiteboard tools for small sessions.",
                0,
                "USD",
                "MONTHLY",
                3,
                5,
                10,
                "FREE",
                1
        );

        seedPlanIfMissing(
                "PREMIUM",
                "Premium",
                "Expanded rooms, larger audiences, and high-performance AI capabilities.",
                900,
                "USD",
                "MONTHLY",
                20,
                25,
                20,
                "PREMIUM",
                2
        );

        seedPlanIfMissing(
                "TEAM",
                "Team",
                "Full scale workshop capacity for organizations and large teams.",
                2400,
                "USD",
                "MONTHLY",
                100,
                100,
                20,
                "PREMIUM",
                3
        );
    }

    private void seedPlanIfMissing(
            String code,
            String name,
            String description,
            int priceCents,
            String currency,
            String billingInterval,
            int maxActiveRooms,
            int maxParticipantsPerRoom,
            int aiDailyLimit,
            String aiTier,
            int sortOrder) {

        if (planRepository.findByCode(code).isEmpty()) {
            Instant now = Instant.now();
            Plan plan = Plan.builder()
                    .id(UUID.randomUUID())
                    .code(code)
                    .name(name)
                    .description(description)
                    .priceCents(priceCents)
                    .currency(currency)
                    .billingInterval(billingInterval)
                    .maxActiveRooms(maxActiveRooms)
                    .maxParticipantsPerRoom(maxParticipantsPerRoom)
                    .aiDailyLimit(aiDailyLimit)
                    .aiTier(aiTier)
                    .active(true)
                    .sortOrder(sortOrder)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            planRepository.save(plan);
            log.info("Seeded initial plan: {}", code);
        } else {
            log.debug("Plan {} already exists; skipping seed to preserve custom changes.", code);
        }
    }
}
