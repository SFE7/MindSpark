package com.sme.maintenance.subscription;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RoomEntitlementEnforcementTests.MockEntitlementConfig.class)
public class RoomEntitlementEnforcementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private EntitlementClient entitlementClient;

    private User ownerUser;
    private String ownerSub;
    private String ownerEmail;

    @TestConfiguration
    static class MockEntitlementConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder");
            };
        }

        @Bean
        @Primary
        EntitlementClient entitlementClient() {
            EntitlementClient mock = Mockito.mock(EntitlementClient.class);
            Mockito.lenient().when(mock.entitlementsFallback(Mockito.any(), Mockito.any()))
                    .thenAnswer(inv -> {
                        Throwable t = inv.getArgument(1);
                        if (t instanceof RuntimeException re) throw re;
                        throw new SubscriptionServiceUnavailableException("Subscription service is temporarily unavailable. Please try again later.", t);
                    });
            return mock;
        }
    }

    @BeforeEach
    void setUp() {
        roomRepository.deleteAll();

        ownerSub = "owner-" + UUID.randomUUID();
        ownerEmail = ownerSub + "@mindspark.com";

        ownerUser = new User();
        ownerUser.setKeycloakId(ownerSub);
        ownerUser.setUsername("room_owner");
        ownerUser.setEmail(ownerEmail);
        ownerUser.setCreatedAt(Date.valueOf(LocalDate.now()));
        ownerUser = userRepository.save(ownerUser);
    }

    @Test
    @DisplayName("Room creation limit enforcement: FREE user hits limit at 3 rooms (403 ROOM_LIMIT_REACHED)")
    void testRoomCreationLimitEnforcement() throws Exception {
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10));

        // Create 3 rooms - all should succeed
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/rooms")
                    .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"name\":\"Room %d\"}", i)))
                    .andExpect(status().isOk());
        }

        // 4th room attempt must fail with 403 ROOM_LIMIT_REACHED
        mockMvc.perform(post("/api/rooms")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Room 4\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ROOM_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.current").value(3))
                .andExpect(jsonPath("$.upgradeRequired").value(true));
    }

    @Test
    @DisplayName("Closing or deleting a room frees a slot for new room creation")
    void testSlotFreedOnCloseOrDelete() throws Exception {
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10));

        // Create 3 rooms
        Room r1 = roomService.create("Room 1", ownerUser.getId());
        Room r2 = roomService.create("Room 2", ownerUser.getId());
        Room r3 = roomService.create("Room 3", ownerUser.getId());

        // Verify active usage endpoint
        mockMvc.perform(get("/api/rooms/usage")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRoomsCount").value(3));

        // Close r1 -> frees slot
        mockMvc.perform(post("/api/rooms/" + r1.getId() + "/close")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail))))
                .andExpect(status().isOk());

        // Now creating a new room succeeds!
        mockMvc.perform(post("/api/rooms")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Room Replacement After Close\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("(a) Join limit strictly uses room owner's plan: FREE joiner can join PREMIUM owner at 6th slot, but PREMIUM joiner cannot join FREE owner at 6th slot")
    void testJoinLimitUsesRoomOwnerPlan() throws Exception {
        // --- SCENARIO 1: Owner is PREMIUM (maxParticipantsPerRoom = 25) ---
        String premOwnerSub = "prem-owner-" + UUID.randomUUID();
        String premOwnerEmail = premOwnerSub + "@mindspark.com";
        User premOwner = createUser(premOwnerSub, "prem_owner", premOwnerEmail);

        when(entitlementClient.getEntitlements(eq(premOwnerSub)))
                .thenReturn(new EntitlementDto(premOwnerSub, "PREMIUM", "Premium", "PREMIUM", 20, 25, 20));

        Room premRoom = roomService.create("Premium Workshop", premOwner.getId());
        String premCode = premRoom.getInvitationCode();

        // Add 4 more participants to make 5 total (premOwner + 4)
        for (int i = 1; i <= 4; i++) {
            User p = createUser("p-prem-" + i + "-" + UUID.randomUUID(), "p_prem_" + i, "pprem" + i + "@mindspark.com");
            roomService.join(premCode, p.getId());
        }
        assertThat(roomService.members(premRoom.getId())).hasSize(5);

        // 6th participant is a FREE user. Because the room OWNER is PREMIUM (limit 25), this FREE user MUST SUCCEED.
        String freeJoinerSub = "free-joiner-" + UUID.randomUUID();
        String freeJoinerEmail = freeJoinerSub + "@mindspark.com";
        createUser(freeJoinerSub, "free_joiner", freeJoinerEmail);

        mockMvc.perform(post("/api/rooms/join")
                .with(jwt().jwt(j -> j.subject(freeJoinerSub).claim("preferred_username", "free_joiner").claim("email", freeJoinerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"invitationCode\":\"%s\"}", premCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(premRoom.getId().toString()));

        assertThat(roomService.members(premRoom.getId())).hasSize(6);

        // --- SCENARIO 2: Owner is FREE (maxParticipantsPerRoom = 5) ---
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10));

        Room freeRoom = roomService.create("Free Session", ownerUser.getId());
        String freeCode = freeRoom.getInvitationCode();

        // Add 4 more participants to make 5 total (ownerUser + 4)
        for (int i = 1; i <= 4; i++) {
            User p = createUser("p-free-" + i + "-" + UUID.randomUUID(), "p_free_" + i, "pfree" + i + "@mindspark.com");
            roomService.join(freeCode, p.getId());
        }
        assertThat(roomService.members(freeRoom.getId())).hasSize(5);

        // 6th participant is a paid PREMIUM user. Even though the joiner has a PREMIUM subscription,
        // the room OWNER is FREE (limit 5), so this join MUST FAIL with 403 PARTICIPANT_LIMIT_REACHED.
        String premJoinerSub = "prem-joiner-" + UUID.randomUUID();
        String premJoinerEmail = premJoinerSub + "@mindspark.com";
        createUser(premJoinerSub, "prem_joiner", premJoinerEmail);

        mockMvc.perform(post("/api/rooms/join")
                .with(jwt().jwt(j -> j.subject(premJoinerSub).claim("preferred_username", "prem_joiner").claim("email", premJoinerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"invitationCode\":\"%s\"}", freeCode)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PARTICIPANT_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.current").value(5));
    }

    @Test
    @DisplayName("(b) Idempotent re-join: existing participant AND room creator can re-join when room is full")
    void testIdempotentRejoinWhenRoomIsFull() throws Exception {
        // Owner is FREE: maxParticipantsPerRoom = 3
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 3, 10));

        Room room = roomService.create("Huddle Room", ownerUser.getId());
        String code = room.getInvitationCode();

        User member2 = createUser("member-2-" + UUID.randomUUID(), "member2", "m2@mindspark.com");
        roomService.join(code, member2.getId());

        User member3 = createUser("member-3-" + UUID.randomUUID(), "member3", "m3@mindspark.com");
        roomService.join(code, member3.getId());

        // Room is now completely full (3/3: ownerUser, member2, member3)
        assertThat(roomService.members(room.getId())).hasSize(3);

        // 1. Room creator (owner) re-joins when full -> MUST SUCCEED (200 OK)
        mockMvc.perform(post("/api/rooms/join")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"invitationCode\":\"%s\"}", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId().toString()));

        // 2. Existing participant (member2) re-joins when full -> MUST SUCCEED (200 OK)
        mockMvc.perform(post("/api/rooms/join")
                .with(jwt().jwt(j -> j.subject(member2.getKeycloakId()).claim("preferred_username", member2.getUsername()).claim("email", member2.getEmail())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"invitationCode\":\"%s\"}", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId().toString()));

        // Confirm member count unchanged
        assertThat(roomService.members(room.getId())).hasSize(3);
    }

    @Test
    @DisplayName("(c) Parallel room-join concurrency at limit-1: exactly 1 succeeds, others get 403 PARTICIPANT_LIMIT_REACHED")
    void testConcurrentRoomJoinAtLimit() throws InterruptedException {
        // Owner has limit 5
        EntitlementDto ownerLimits = new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10);
        when(entitlementClient.getEntitlements(eq(ownerSub))).thenReturn(ownerLimits);

        Room room = roomService.create("Concurrency Join Test Room", ownerUser.getId());
        String code = room.getInvitationCode();

        // Pre-fill to 4 participants (ownerUser + 3 members): exactly 1 slot remains
        for (int i = 1; i <= 3; i++) {
            User p = createUser("pre-joiner-" + i + "-" + UUID.randomUUID(), "pre_" + i, "pre" + i + "@mindspark.com");
            roomService.join(code, p.getId());
        }
        assertThat(roomService.members(room.getId())).hasSize(4);

        int candidateCount = 10;
        User[] candidates = new User[candidateCount];
        for (int i = 0; i < candidateCount; i++) {
            candidates[i] = createUser("cand-" + i + "-" + UUID.randomUUID(), "cand_" + i, "cand" + i + "@mindspark.com");
        }

        ExecutorService executor = Executors.newFixedThreadPool(candidateCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(candidateCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitHitCount = new AtomicInteger(0);

        for (int i = 0; i < candidateCount; i++) {
            final User candidate = candidates[i];
            executor.submit(() -> {
                try {
                    startLatch.await();
                    roomService.joinWithLimits(code, candidate.getId(), ownerLimits);
                    successCount.incrementAndGet();
                } catch (ParticipantLimitReachedException e) {
                    limitHitCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
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
        assertThat(limitHitCount.get()).isEqualTo(candidateCount - 1);
        assertThat(roomService.members(room.getId())).hasSize(5);
    }

    @Test
    @DisplayName("(d) 503 fail-closed: room create and room join return HTTP 503 SUBSCRIPTION_SERVICE_UNAVAILABLE when service is unreachable")
    void testFailClosed503WhenSubscriptionServiceUnavailable() throws Exception {
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenThrow(new SubscriptionServiceUnavailableException("Connection refused"));

        // 1. Room create fail-closed
        mockMvc.perform(post("/api/rooms")
                .with(jwt().jwt(j -> j.subject(ownerSub).claim("preferred_username", "room_owner").claim("email", ownerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Fail-Closed Room\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_SERVICE_UNAVAILABLE"));

        // Set up a room to test join fail-closed
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10));
        Room room = roomService.create("Join Test Room", ownerUser.getId());
        String code = room.getInvitationCode();

        // Now simulate subscription-service failure when fetching owner limits for join
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenThrow(new SubscriptionServiceUnavailableException("Connection timeout"));

        String newJoinerSub = "new-joiner-" + UUID.randomUUID();
        String newJoinerEmail = newJoinerSub + "@mindspark.com";
        createUser(newJoinerSub, "new_joiner", newJoinerEmail);

        // 2. Room join fail-closed
        mockMvc.perform(post("/api/rooms/join")
                .with(jwt().jwt(j -> j.subject(newJoinerSub).claim("preferred_username", "new_joiner").claim("email", newJoinerEmail)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"invitationCode\":\"%s\"}", code)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Parallel room-creation concurrency at limit-1: allows exactly 1 to succeed, remaining get 403")
    void testConcurrentRoomCreationAtLimit() throws InterruptedException {
        // Owner has limit 3, already has 2 active rooms
        when(entitlementClient.getEntitlements(eq(ownerSub)))
                .thenReturn(new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10));

        roomService.create("Existing 1", ownerUser.getId());
        roomService.create("Existing 2", ownerUser.getId());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitHitCount = new AtomicInteger(0);

        EntitlementDto limits = new EntitlementDto(ownerSub, "FREE", "Free", "FREE", 3, 5, 10);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    roomService.create("Concurrent Room " + idx, ownerUser.getId(), limits);
                    successCount.incrementAndGet();
                } catch (RoomLimitReachedException e) {
                    limitHitCount.incrementAndGet();
                } catch (Exception e) {
                    // Any other
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
        assertThat(limitHitCount.get()).isEqualTo(threadCount - 1);
        assertThat(roomService.countActiveRooms(ownerUser.getId())).isEqualTo(3);
    }

    private User createUser(String sub, String username, String email) {
        User u = new User();
        u.setKeycloakId(sub);
        u.setUsername(username);
        u.setEmail(email);
        u.setCreatedAt(Date.valueOf(LocalDate.now()));
        return userRepository.save(u);
    }
}
