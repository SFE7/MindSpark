package com.mindspark.subscription;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import com.mindspark.subscription.dto.AdminAssignRequest;
import com.mindspark.subscription.dto.CreateAdminRequest;
import com.mindspark.subscription.dto.CreatePlanRequest;
import com.mindspark.subscription.dto.UpdatePlanRequest;
import com.mindspark.subscription.entity.BillingEventType;
import com.mindspark.subscription.entity.BillingRecord;
import com.mindspark.subscription.entity.BillingRecordStatus;
import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.entity.Subscription;
import com.mindspark.subscription.entity.SubscriptionStatus;
import com.mindspark.subscription.repository.BillingRecordRepository;
import com.mindspark.subscription.repository.PlanRepository;
import com.mindspark.subscription.repository.SubscriptionRepository;
import com.mindspark.subscription.security.JwtAuthConverter;
import com.mindspark.subscription.service.DatabaseInitializer;
import com.mindspark.subscription.service.KeycloakAdminUserService;
import com.mindspark.subscription.service.KeycloakRoleSyncService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/mindspark",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/mindspark/protocol/openid-connect/certs",
    "keycloak.admin.base-url=http://localhost:8180",
    "keycloak.admin.realm=mindspark",
    "keycloak.admin.client-id=mindspark-admin-cli",
    "keycloak.admin.client-secret=test-secret"
})
class AdminEndpointsSecurityAndFunctionalTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private DatabaseInitializer databaseInitializer;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private JwtAuthConverter jwtAuthConverter;

    private MockMvc mockMvc;

    private RequestPostProcessor withKeycloakRoles(String username, String... roles) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", username)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        var auth = jwtAuthConverter.convert(jwt);
        return jwt().jwt(builder -> builder
                .subject(username)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(roles)))
        ).authorities(auth.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        billingRecordRepository.deleteAll();
        subscriptionRepository.deleteAll();
        databaseInitializer.run(null);
    }

    // ==========================================
    // PHASE 1 & STORY 61: ROLE HIERARCHY & SECURITY
    // ==========================================

    @Test
    @DisplayName("Unauthenticated request to /api/admin/** returns 401 Unauthorized")
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/plans"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/subscriptions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/stats/billing"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/admins"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Regular user (non-admin) to /api/admin/** returns 403 Forbidden")
    void regularUser_shouldReturn403() throws Exception {
        var userAuth = withKeycloakRoles("regularuser", "user", "default-roles-mindspark");

        mockMvc.perform(get("/api/admin/plans").with(userAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/subscriptions").with(userAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats/billing").with(userAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/admins").with(userAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin role: can access plans, subscriptions, stats, but gets 403 on /api/admin/admins")
    void adminUser_canAccessStandardAdmin_butForbiddenOnAdmins() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");

        mockMvc.perform(get("/api/admin/plans").with(adminAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/subscriptions").with(adminAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/stats/billing").with(adminAuth))
                .andExpect(status().isOk());

        // Strictly SUPER_ADMIN only
        mockMvc.perform(get("/api/admin/admins").with(adminAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/admins")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"firstName\":\"New\",\"lastName\":\"Admin\",\"temporaryPassword\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Super-Admin role: can access /api/admin/admins and other admin endpoints")
    void superAdminUser_canAccessEverything() throws Exception {
        var superAdminAuth = withKeycloakRoles("superadmin", "super-admin");

        mockMvc.perform(get("/api/admin/plans").with(superAdminAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/admins").with(superAdminAuth))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JwtAuthConverter maps 'admin' to ROLE_ADMIN and 'super-admin' to ROLE_SUPER_ADMIN")
    void jwtAuthConverter_mapsRolesCorrectly() {
        Jwt adminJwt = Jwt.withTokenValue("t1")
                .header("alg", "none")
                .claim("sub", "u1")
                .claim("preferred_username", "u1")
                .claim("realm_access", Map.of("roles", List.of("admin", "premium")))
                .build();
        var adminToken = jwtAuthConverter.convert(adminJwt);
        assertThat(adminToken.getAuthorities()).extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_admin", "ROLE_PREMIUM", "ROLE_premium");

        Jwt superJwt = Jwt.withTokenValue("t2")
                .header("alg", "none")
                .claim("sub", "u2")
                .claim("preferred_username", "u2")
                .claim("realm_access", Map.of("roles", List.of("super-admin")))
                .build();
        var superToken = jwtAuthConverter.convert(superJwt);
        assertThat(superToken.getAuthorities()).extracting("authority")
                .contains("ROLE_SUPER_ADMIN", "ROLE_super_admin");
    }

    // ==========================================
    // STORY 63: MANAGE PLANS & PRICING
    // ==========================================

    @Test
    @DisplayName("Story 63: Create plan successfully with valid data")
    void createPlan_success() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");

        CreatePlanRequest req = new CreatePlanRequest(
                "SCALE",
                "Scale Plan",
                "For high-volume workshops",
                4900,
                "USD",
                "MONTHLY",
                50,
                50,
                100,
                "PREMIUM",
                4
        );

        mockMvc.perform(post("/api/admin/plans")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "code": "SCALE",
                            "name": "Scale Plan",
                            "description": "For high-volume workshops",
                            "priceCents": 4900,
                            "currency": "USD",
                            "billingInterval": "MONTHLY",
                            "maxActiveRooms": 50,
                            "maxParticipantsPerRoom": 50,
                            "aiDailyLimit": 100,
                            "aiTier": "PREMIUM",
                            "sortOrder": 4
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SCALE"))
                .andExpect(jsonPath("$.priceCents").value(4900))
                .andExpect(jsonPath("$.aiTier").value("PREMIUM"));

        assertThat(planRepository.findByCode("SCALE")).isPresent();
    }

    @Test
    @DisplayName("Story 63: Create plan with duplicate code returns 409 PLAN_CODE_EXISTS")
    void createPlan_duplicateCode_returns409() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");

        mockMvc.perform(post("/api/admin/plans")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "code": "PREMIUM",
                            "name": "Duplicate",
                            "description": "Desc",
                            "priceCents": 1000,
                            "currency": "USD",
                            "billingInterval": "MONTHLY",
                            "maxActiveRooms": 10,
                            "maxParticipantsPerRoom": 10,
                            "aiDailyLimit": 20,
                            "aiTier": "PREMIUM",
                            "sortOrder": 5
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PLAN_CODE_EXISTS"));
    }

    @Test
    @DisplayName("Story 63: Update plan price and limits preserves existing immutable billing records")
    void updatePlan_preservesExistingBillingRecords() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");

        Plan premium = planRepository.findByCode("PREMIUM").orElseThrow();

        // Create an existing billing record referencing this plan at $9.00
        BillingRecord priorRecord = BillingRecord.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-999001")
                .userId("user-abc")
                .eventType(BillingEventType.SUBSCRIBE)
                .planCode("PREMIUM")
                .planName("Premium")
                .amountCents(900)
                .currency("USD")
                .status(BillingRecordStatus.PAID)
                .paymentMethodSummary("Card •••• 4242")
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
        billingRecordRepository.save(priorRecord);

        mockMvc.perform(put("/api/admin/plans/" + premium.getId())
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Premium Plus",
                            "description": "Updated description",
                            "priceCents": 1500,
                            "currency": "USD",
                            "billingInterval": "MONTHLY",
                            "maxActiveRooms": 25,
                            "maxParticipantsPerRoom": 30,
                            "aiDailyLimit": 40,
                            "aiTier": "PREMIUM",
                            "active": true,
                            "sortOrder": 2
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCents").value(1500))
                .andExpect(jsonPath("$.name").value("Premium Plus"));

        // Verify billing record snapshot was completely untouched
        BillingRecord fetchedRecord = billingRecordRepository.findById(priorRecord.getId()).orElseThrow();
        assertThat(fetchedRecord.getAmountCents()).isEqualTo(900);
        assertThat(fetchedRecord.getPlanName()).isEqualTo("Premium");
    }

    @Test
    @DisplayName("Story 63: FREE plan cannot be deactivated or priced > 0")
    void freePlan_invariantsEnforced() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");
        Plan freePlan = planRepository.findByCode("FREE").orElseThrow();

        // 1. Attempt to deactivate FREE via update -> 400
        mockMvc.perform(put("/api/admin/plans/" + freePlan.getId())
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Free",
                            "description": "Desc",
                            "priceCents": 0,
                            "currency": "USD",
                            "billingInterval": "MONTHLY",
                            "maxActiveRooms": 3,
                            "maxParticipantsPerRoom": 5,
                            "aiDailyLimit": 10,
                            "aiTier": "FREE",
                            "active": false,
                            "sortOrder": 1
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PLAN_UPDATE"));

        // 2. Attempt to set FREE price > 0 -> 400
        mockMvc.perform(put("/api/admin/plans/" + freePlan.getId())
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Free",
                            "description": "Desc",
                            "priceCents": 500,
                            "currency": "USD",
                            "billingInterval": "MONTHLY",
                            "maxActiveRooms": 3,
                            "maxParticipantsPerRoom": 5,
                            "aiDailyLimit": 10,
                            "aiTier": "FREE",
                            "active": true,
                            "sortOrder": 1
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PLAN_UPDATE"));

        // 3. Attempt to deactivate FREE via toggle endpoint -> 400
        mockMvc.perform(patch("/api/admin/plans/" + freePlan.getId() + "/status")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PLAN_UPDATE"));
    }

    // ==========================================
    // STORY 62: MANAGE SUBSCRIPTIONS
    // ==========================================

    @Test
    @DisplayName("Story 62: Admin assigns plan to implicit-FREE user (creates sub, ADMIN_ASSIGN invoice)")
    void adminAssign_toImplicitFreeUser() throws Exception {
        var adminAuth = withKeycloakRoles("superadmin", "super-admin");

        String targetUserId = "user-implicit-free";

        mockMvc.perform(post("/api/admin/subscriptions/assign")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "userId": "user-implicit-free",
                            "planCode": "PREMIUM",
                            "reason": "Support ticket bonus"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify active subscription exists
        Subscription sub = subscriptionRepository.findByUserIdAndStatus(targetUserId, SubscriptionStatus.ACTIVE).orElseThrow();
        assertThat(sub.getPlan().getCode()).isEqualTo("PREMIUM");

        // Verify ADMIN_ASSIGN billing record
        List<BillingRecord> records = billingRecordRepository.findByUserIdOrderByCreatedAtDesc(targetUserId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getEventType()).isEqualTo(BillingEventType.ADMIN_ASSIGN);
        assertThat(records.get(0).getAmountCents()).isEqualTo(0);
        assertThat(records.get(0).getPaymentMethodSummary()).contains("Admin Override: superadmin");
    }

    @Test
    @DisplayName("Story 62: Admin cancels user subscription (records ADMIN_CANCEL invoice)")
    void adminCancel_activeSubscription() throws Exception {
        var adminAuth = withKeycloakRoles("admin-joe", "admin");

        String userId = "user-with-sub";
        Plan teamPlan = planRepository.findByCode("TEAM").orElseThrow();
        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(teamPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .currentPeriodStart(Instant.now().minus(10, ChronoUnit.DAYS))
                .currentPeriodEnd(Instant.now().plus(20, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();
        subscriptionRepository.save(activeSub);

        mockMvc.perform(post("/api/admin/subscriptions/" + userId + "/cancel")
                        .with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.status").value("CANCELED"));

        // Verify subscription is now CANCELED
        Subscription updated = subscriptionRepository.findById(activeSub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        // Verify ADMIN_CANCEL invoice created
        List<BillingRecord> records = billingRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(records).anyMatch(r -> r.getEventType() == BillingEventType.ADMIN_CANCEL && r.getAmountCents() == 0);
    }

    // ==========================================
    // STORY 64: BILLING STATS ACCURACY
    // ==========================================

    @Test
    @DisplayName("Story 64: Billing stats returns accurate subscription counts, revenue, and MRR")
    void getBillingStats_accuracy() throws Exception {
        var adminAuth = withKeycloakRoles("adminuser", "admin");

        Plan premium = planRepository.findByCode("PREMIUM").orElseThrow();
        Plan team = planRepository.findByCode("TEAM").orElseThrow();

        // 2 active subs: 1 Premium ($9.00), 1 Team ($24.00) -> MRR = $33.00 (3300 cents)
        subscriptionRepository.save(Subscription.builder()
                .id(UUID.randomUUID()).userId("user-1").plan(premium).status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now()).currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        subscriptionRepository.save(Subscription.builder()
                .id(UUID.randomUUID()).userId("user-2").plan(team).status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now()).currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        // Settled billing records: $900 + $2400 = 3300 cents
        billingRecordRepository.save(BillingRecord.builder()
                .id(UUID.randomUUID()).invoiceNumber("INV-001").userId("user-1").eventType(BillingEventType.SUBSCRIBE)
                .planCode("PREMIUM").planName("Premium").amountCents(900).currency("USD").status(BillingRecordStatus.PAID)
                .paymentMethodSummary("Card").createdAt(Instant.now()).build());

        billingRecordRepository.save(BillingRecord.builder()
                .id(UUID.randomUUID()).invoiceNumber("INV-002").userId("user-2").eventType(BillingEventType.SUBSCRIBE)
                .planCode("TEAM").planName("Team").amountCents(2400).currency("USD").status(BillingRecordStatus.PAID)
                .paymentMethodSummary("Card").createdAt(Instant.now()).build());

        mockMvc.perform(get("/api/admin/stats/billing").with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubscriptionsCount").value(2))
                .andExpect(jsonPath("$.activePaidSubscriptionsCount").value(2))
                .andExpect(jsonPath("$.totalRevenueCents").value(3300))
                .andExpect(jsonPath("$.estimatedMrrCents").value(3300));
    }
}
