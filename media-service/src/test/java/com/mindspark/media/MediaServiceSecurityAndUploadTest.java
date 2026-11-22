package com.mindspark.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.media.api.MediaController;
import com.mindspark.media.client.BackendRoomClient;
import com.mindspark.media.config.MediaSecurityConfig;
import com.mindspark.media.dto.RoomAccessResponse;
import com.mindspark.media.security.JwtAuthConverter;
import com.mindspark.media.service.MediaStorageService;
import com.mindspark.media.validation.ImageBinaryValidator;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@Import({
        MediaSecurityConfig.class,
        JwtAuthConverter.class,
        ImageBinaryValidator.class,
        MediaServiceSecurityAndUploadTest.TestConfig.class
})
@TestPropertySource(properties = {
        "mindspark.cors.allowed-origins=http://localhost:4200",
        "mindspark.media.storage-dir=${java.io.tmpdir}/media-test"
})
class MediaServiceSecurityAndUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BackendRoomClient backendRoomClient;

    @Autowired
    private MediaStorageService mediaStorageService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }

        @Bean
        BackendRoomClient backendRoomClient() {
            BackendRoomClient mock = Mockito.mock(BackendRoomClient.class);
            Mockito.lenient().when(mock.fetchRoomAccessFallback(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenAnswer(inv -> {
                        Throwable t = inv.getArgument(2);
                        if (t instanceof RuntimeException re) throw re;
                        throw new RuntimeException(t);
                    });
            return mock;
        }

        @Bean
        MediaStorageService mediaStorageService(ObjectMapper objectMapper) throws Exception {
            Path tempDir = Files.createTempDirectory("media-test-dir");
            return new MediaStorageService(tempDir.toString(), objectMapper);
        }
    }

    private byte[] createValidPng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createValidJpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    private byte[] createValidWebpLossless(int width, int height) {
        // Minimal valid VP8L header
        // width and height encoded in 14 bits each: (w - 1) | ((h - 1) << 14)
        int wMinus1 = width - 1;
        int hMinus1 = height - 1;
        int dim = (wMinus1 & 0x3FFF) | ((hMinus1 & 0x3FFF) << 14);

        byte[] payload = new byte[32];
        // RIFF
        payload[0] = 'R'; payload[1] = 'I'; payload[2] = 'F'; payload[3] = 'F';
        int riffSize = 24;
        payload[4] = (byte) (riffSize & 0xFF);
        payload[5] = (byte) ((riffSize >> 8) & 0xFF);
        payload[6] = (byte) ((riffSize >> 16) & 0xFF);
        payload[7] = (byte) ((riffSize >> 24) & 0xFF);
        // WEBP
        payload[8] = 'W'; payload[9] = 'E'; payload[10] = 'B'; payload[11] = 'P';
        // VP8L
        payload[12] = 'V'; payload[13] = 'P'; payload[14] = '8'; payload[15] = 'L';
        int chunkSize = 12;
        payload[16] = (byte) (chunkSize & 0xFF);
        payload[17] = (byte) ((chunkSize >> 8) & 0xFF);
        payload[18] = (byte) ((chunkSize >> 16) & 0xFF);
        payload[19] = (byte) ((chunkSize >> 24) & 0xFF);
        // Signature
        payload[20] = 0x2F;
        // Dimensions
        payload[21] = (byte) (dim & 0xFF);
        payload[22] = (byte) ((dim >> 8) & 0xFF);
        payload[23] = (byte) ((dim >> 16) & 0xFF);
        payload[24] = (byte) ((dim >> 24) & 0xFF);
        return payload;
    }

    @Test
    @DisplayName("Health endpoint returns 200 OK without authentication")
    void healthCheck_returns200() throws Exception {
        mockMvc.perform(get("/api/media/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Upload valid PNG returns 201 Created and GET returns 200 with security headers")
    void uploadAndDownloadValidPng_success() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-123";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] pngBytes = createValidPng(120, 80);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        String responseContent = mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").isNotEmpty())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.width").value(120))
                .andExpect(jsonPath("$.height").value(80))
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(responseContent);
        String mediaId = json.get("mediaId").asText();

        // Download
        mockMvc.perform(get("/api/media/rooms/" + roomId + "/" + mediaId)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string("Cache-Control", "private, max-age=3600"));
    }

    @Test
    @DisplayName("Upload valid JPEG returns 201 Created")
    void uploadValidJpeg_success() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-jpeg";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] jpegBytes = createValidJpeg(200, 150);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(200))
                .andExpect(jsonPath("$.height").value(150));
    }

    @Test
    @DisplayName("Upload valid WebP returns 201 Created and GET returns 200")
    void uploadValidWebp_success() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-webp";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] webpBytes = createValidWebpLossless(320, 240);
        MockMultipartFile file = new MockMultipartFile("file", "image.webp", "image/webp", webpBytes);

        String resp = mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.width").value(320))
                .andExpect(jsonPath("$.height").value(240))
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(resp);
        String mediaId = json.get("mediaId").asText();

        mockMvc.perform(get("/api/media/rooms/" + roomId + "/" + mediaId)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"));
    }

    @Test
    @DisplayName("Reject SVG disguised as PNG with 415 UNSUPPORTED_MEDIA_TYPE")
    void uploadSvgDisguisedAsPng_rejectedWith415() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-svg";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", svgBytes);

        mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Reject corrupt fake JPEG binary with 400 INVALID_IMAGE")
    void uploadFakeJpeg_rejectedWith400() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-fake";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] fakeJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5};
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", fakeJpeg);

        mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reject oversized file > 5 MB with 413 FILE_TOO_LARGE")
    void uploadOversizedFile_rejectedWith413() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "user-large";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));

        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1024];
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeBytes);

        mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("Non-member is rejected with 403 Forbidden")
    void nonMemberUpload_rejectedWith403() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String userSub = "stranger";
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(userSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), false, false, false));

        byte[] pngBytes = createValidPng(50, 50);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Path traversal in roomId is rejected with 400 Bad Request")
    void pathTraversalRoomId_rejectedWith400() throws Exception {
        String userSub = "user-trav";
        byte[] pngBytes = createValidPng(50, 50);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/media/rooms/..%2F..%2Fetc")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userSub))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Delete media by uploader or owner succeeds with 204 No Content; unauthorized returns 403")
    void deleteMedia_authorizationCheck() throws Exception {
        String roomId = "room-" + UUID.randomUUID();
        String uploaderSub = "uploader-1";
        String otherSub = "other-user";
        String ownerSub = "owner-1";

        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(uploaderSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(otherSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, false, false));
        when(backendRoomClient.fetchRoomAccess(eq(roomId), eq(ownerSub)))
                .thenReturn(new RoomAccessResponse(UUID.randomUUID(), true, true, false));

        // First upload
        byte[] pngBytes = createValidPng(80, 80);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        String resp = mockMvc.perform(multipart("/api/media/rooms/" + roomId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(uploaderSub))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(resp);
        String mediaId = json.get("mediaId").asText();

        // Unauthorized user attempts delete -> 403
        mockMvc.perform(delete("/api/media/rooms/" + roomId + "/" + mediaId)
                        .with(jwt().jwt(j -> j.subject(otherSub))))
                .andExpect(status().isForbidden());

        // Room owner attempts delete -> 204
        mockMvc.perform(delete("/api/media/rooms/" + roomId + "/" + mediaId)
                        .with(jwt().jwt(j -> j.subject(ownerSub))))
                .andExpect(status().isNoContent());

        // File is deleted, GET returns 404
        mockMvc.perform(get("/api/media/rooms/" + roomId + "/" + mediaId)
                        .with(jwt().jwt(j -> j.subject(ownerSub))))
                .andExpect(status().isNotFound());
    }
}
