package com.mindspark.session;

import java.time.Instant;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mindspark.session.client.BackendRoomClient;
import com.mindspark.session.dto.BackendCategoryDto;
import com.mindspark.session.dto.BackendIdeaDto;
import com.mindspark.session.dto.BackendParticipantDto;
import com.mindspark.session.dto.BackendRoomExportDto;
import com.mindspark.session.exception.RoomNotFoundException;
import com.mindspark.session.exception.RoomSourceUnavailableException;
import com.mindspark.session.repository.SessionSnapshotRepository;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionSnapshotLifecycleAndSecurityTest.TestConfig.class)
public class SessionSnapshotLifecycleAndSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BackendRoomClient backendRoomClient;

    @Autowired
    private SessionSnapshotRepository snapshotRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock Decoder");
            };
        }

        @Bean
        @Primary
        BackendRoomClient backendRoomClient() {
            BackendRoomClient mock = Mockito.mock(BackendRoomClient.class);
            Mockito.lenient().when(mock.fetchRoomExportFallback(Mockito.any(), Mockito.any()))
                    .thenAnswer(inv -> {
                        Throwable t = inv.getArgument(1);
                        if (t instanceof RuntimeException re) throw re;
                        throw new RuntimeException(t);
                    });
            return mock;
        }
    }

    private static final String OWNER_SUB = "kc-owner-sub-100";
    private static final String MEMBER_SUB = "kc-member-sub-200";
    private static final String STRANGER_SUB = "kc-stranger-sub-999";
    private static final String ADMIN_SUB = "kc-admin-sub-001";

    private RequestPostProcessor asUser(String sub, String username) {
        return jwt().jwt(jwt -> jwt
                .subject(sub)
                .claim("preferred_username", username));
    }

    private RequestPostProcessor asAdmin(String sub, String username) {
        return jwt().jwt(jwt -> jwt
                .subject(sub)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "ROLE_ADMIN"))));
    }

    private BackendRoomExportDto createSampleRoom(UUID roomId) {
        return new BackendRoomExportDto(
                roomId,
                "Sprint Planning & Retrospective",
                "CODE5678",
                false,
                Instant.now(),
                OWNER_SUB,
                "OwnerUser",
                List.of(
                        new BackendParticipantDto(OWNER_SUB, "OwnerUser", "OWNER"),
                        new BackendParticipantDto(MEMBER_SUB, "MemberUser", "MEMBER")
                ),
                List.of(
                        new BackendCategoryDto(1L, "Sprint Goals", null)
                ),
                List.of(
                        new BackendIdeaDto(
                                UUID.randomUUID(),
                                "Ship Sprint 3 Phase 3",
                                "Implement session-service microservice",
                                1L,
                                "Sprint Goals",
                                "MemberUser",
                                MEMBER_SUB,
                                10,
                                Instant.now(),
                                List.of()
                        )
                )
        );
    }

    @BeforeEach
    void cleanDb() {
        snapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("Unauthenticated request to POST /api/sessions/snapshots returns 401")
    void saveSnapshot_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/sessions/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-member user trying to save room snapshot is rejected with 403 Forbidden")
    void saveSnapshot_nonMember_returns403() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(STRANGER_SUB, "Stranger"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", equalTo("FORBIDDEN")));
    }

    @Test
    @DisplayName("Participant user successfully saves snapshot, returns 201 Created with immutable payload")
    void saveSnapshot_member_returns201() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"title\":\"Sprint 3 Architecture Snapshot\",\"notes\":\"Key takeaways\",\"aiSummary\":\"AI action plan summary\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.roomId", equalTo(roomId.toString())))
                .andExpect(jsonPath("$.title", equalTo("Sprint 3 Architecture Snapshot")))
                .andExpect(jsonPath("$.notes", equalTo("Key takeaways")))
                .andExpect(jsonPath("$.aiSummary", equalTo("AI action plan summary")))
                .andExpect(jsonPath("$.savedByUserId", equalTo(MEMBER_SUB)))
                .andExpect(jsonPath("$.ideaCount", equalTo(1)))
                .andExpect(jsonPath("$.totalVotes", equalTo(10)))
                .andExpect(jsonPath("$.snapshotData.roomName", equalTo("Sprint Planning & Retrospective")));
    }

    @Test
    @DisplayName("Backend returns 404 for unknown room -> maps to 404 ROOM_NOT_FOUND")
    void saveSnapshot_backend404_returns404() throws Exception {
        String nonExistentRoom = UUID.randomUUID().toString();
        Mockito.when(backendRoomClient.fetchRoomExportData(nonExistentRoom))
                .thenThrow(new RoomNotFoundException("Room not found: " + nonExistentRoom));

        mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + nonExistentRoom + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("ROOM_NOT_FOUND")));
    }

    @Test
    @DisplayName("Backend unreachable/5xx -> maps to 503 ROOM_SOURCE_UNAVAILABLE")
    void saveSnapshot_backend503_returns503() throws Exception {
        String roomId = UUID.randomUUID().toString();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId))
                .thenThrow(new RoomSourceUnavailableException("Backend service unreachable"));

        mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", equalTo("ROOM_SOURCE_UNAVAILABLE")));
    }

    @Test
    @DisplayName("List snapshots only returns snapshots accessible by caller (member/owner/admin)")
    void listSnapshots_accessFiltering() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        // Save as Member
        mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"title\":\"Member Snapshot\"}"))
                .andExpect(status().isCreated());

        // Stranger lists: sees 0 snapshots
        mockMvc.perform(get("/api/sessions/snapshots")
                .with(asUser(STRANGER_SUB, "Stranger")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Member lists: sees 1 snapshot
        mockMvc.perform(get("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", equalTo("Member Snapshot")));

        // Owner lists: sees 1 snapshot
        mockMvc.perform(get("/api/sessions/snapshots")
                .with(asUser(OWNER_SUB, "OwnerUser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Admin lists: sees 1 snapshot
        mockMvc.perform(get("/api/sessions/snapshots")
                .with(asAdmin(ADMIN_SUB, "AdminUser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Get snapshot by non-member returns 403; by member returns 200 with full hierarchy")
    void getSnapshot_securityAndDetail() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        String res = mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"title\":\"Detail Test Snapshot\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String snapshotId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(res).get("id").asText();

        // Stranger gets snapshot -> 403
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId)
                .with(asUser(STRANGER_SUB, "Stranger")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", equalTo("FORBIDDEN")));

        // Member gets snapshot -> 200 with room data
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId)
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(snapshotId)))
                .andExpect(jsonPath("$.title", equalTo("Detail Test Snapshot")))
                .andExpect(jsonPath("$.snapshotData.ideas", hasSize(1)))
                .andExpect(jsonPath("$.snapshotData.ideas[0].title", equalTo("Ship Sprint 3 Phase 3")));
    }

    @Test
    @DisplayName("Delete snapshot by non-owner returns 403; by saver returns 204")
    void deleteSnapshot_permissions() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        String res = mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"title\":\"To Be Deleted\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String snapshotId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(res).get("id").asText();

        // Stranger tries to delete -> 403
        mockMvc.perform(delete("/api/sessions/snapshots/" + snapshotId)
                .with(asUser(STRANGER_SUB, "Stranger")))
                .andExpect(status().isForbidden());

        // Saver deletes -> 204
        mockMvc.perform(delete("/api/sessions/snapshots/" + snapshotId)
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId)
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Export saved snapshot returns 200 with Content-Disposition header and format validation")
    void exportSnapshot_formatsAndHeaders() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        String res = mockMvc.perform(post("/api/sessions/snapshots")
                .with(asUser(MEMBER_SUB, "MemberUser"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"title\":\"Export Test Session\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String snapshotId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(res).get("id").asText();

        // Markdown export
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId + "/export?format=md")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));

        // JSON export
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId + "/export?format=json")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));

        // CSV export
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId + "/export?format=csv")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));

        // Invalid format -> 400
        mockMvc.perform(get("/api/sessions/snapshots/" + snapshotId + "/export?format=pdf")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("INVALID_EXPORT_FORMAT")));
    }

    @Test
    @DisplayName("Export live room directly checks membership and returns export")
    void exportLiveRoom_directExport() throws Exception {
        UUID roomId = UUID.randomUUID();
        Mockito.when(backendRoomClient.fetchRoomExportData(roomId.toString()))
                .thenReturn(createSampleRoom(roomId));

        // Stranger tries live export -> 403
        mockMvc.perform(get("/api/sessions/rooms/" + roomId + "/export?format=md")
                .with(asUser(STRANGER_SUB, "Stranger")))
                .andExpect(status().isForbidden());

        // Member live export -> 200
        mockMvc.perform(get("/api/sessions/rooms/" + roomId + "/export?format=md")
                .with(asUser(MEMBER_SUB, "MemberUser")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));
    }
}
