package com.mindspark.session;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.mindspark.session.dto.BackendCategoryDto;
import com.mindspark.session.dto.BackendCommentDto;
import com.mindspark.session.dto.BackendIdeaDto;
import com.mindspark.session.dto.BackendParticipantDto;
import com.mindspark.session.dto.BackendRoomExportDto;
import com.mindspark.session.exception.InvalidExportFormatException;
import com.mindspark.session.service.SessionExportService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionExportServiceTest {

    private SessionExportService exportService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        exportService = new SessionExportService(objectMapper);
    }

    private BackendRoomExportDto createSampleRoom() {
        UUID roomId = UUID.randomUUID();
        UUID idea1Id = UUID.randomUUID();
        UUID idea2Id = UUID.randomUUID();
        UUID idea3Id = UUID.randomUUID();
        UUID c1Id = UUID.randomUUID();

        return new BackendRoomExportDto(
                roomId,
                "جلسة العصف الذهني & Projet Démo",
                "CODE1234",
                false,
                Instant.now(),
                "kc-owner-1",
                "Ahmed Trabelsi",
                List.of(
                        new BackendParticipantDto("kc-owner-1", "Ahmed Trabelsi", "OWNER"),
                        new BackendParticipantDto("kc-member-2", "Sarah Mansour", "MEMBER")
                ),
                List.of(
                        new BackendCategoryDto(10L, "Architecture & Conception", null),
                        new BackendCategoryDto(20L, "تصميم الواجهة", null)
                ),
                List.of(
                        new BackendIdeaDto(
                                idea1Id,
                                "Microservice Session avec Spring Boot",
                                "Détails techniques de l'implémentation",
                                10L,
                                "Architecture & Conception",
                                "Ahmed Trabelsi",
                                "kc-owner-1",
                                5,
                                Instant.now().minusSeconds(100),
                                List.of(new BackendCommentDto(c1Id, "Sarah", "Excellente idée!", null, Instant.now()))
                        ),
                        new BackendIdeaDto(
                                idea2Id,
                                "=SUM(A1:B10)", // Formula injection test candidate
                                "+cmd|' /C calc'!A0",
                                20L,
                                "تصميم الواجهة",
                                "Malicious User",
                                "kc-bad-1",
                                1,
                                Instant.now(),
                                List.of()
                        ),
                        new BackendIdeaDto(
                                idea3Id,
                                "فكرة غير مصنفة",
                                "تفاصيل الفكرة باللغة العربية",
                                null,
                                null,
                                "Sarah Mansour",
                                "kc-member-2",
                                3,
                                Instant.now().minusSeconds(50),
                                List.of()
                        )
                )
        );
    }

    @Test
    @DisplayName("Markdown renderer formats session report with categories, comments, and Arabic/French UTF-8 intact")
    void renderMarkdown_preservesUtf8AndStructure() {
        BackendRoomExportDto room = createSampleRoom();
        String md = exportService.renderMarkdown("Session d'Idéation #1", "Notes importantes", "AI Résumé automatique", room);

        assertThat(md).contains("# Session d'Idéation #1");
        assertThat(md).contains("جلسة العصف الذهني & Projet Démo");
        assertThat(md).contains("## 📝 Session Notes");
        assertThat(md).contains("Notes importantes");
        assertThat(md).contains("## ✨ AI Summary & Action Plan");
        assertThat(md).contains("AI Résumé automatique");
        assertThat(md).contains("## 🏆 Top Voted Ideas");
        assertThat(md).contains("Microservice Session avec Spring Boot");
        assertThat(md).contains("Architecture & Conception");
        assertThat(md).contains("تصميم الواجهة");
        assertThat(md).contains("فكرة غير مصنفة");
        assertThat(md).contains("💬 **Sarah**: Excellente idée!");
    }

    @Test
    @DisplayName("JSON export generates valid parseable JSON containing all room data")
    void renderJson_validParseablePayload() throws Exception {
        BackendRoomExportDto room = createSampleRoom();
        String json = exportService.renderJson("Test Export", "Test Notes", "Test AI", room);

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.has("exportTitle")).isTrue();
        assertThat(root.get("exportTitle").asText()).isEqualTo("Test Export");
        assertThat(root.get("notes").asText()).isEqualTo("Test Notes");
        assertThat(root.get("aiSummary").asText()).isEqualTo("Test AI");
        assertThat(root.has("room")).isTrue();
        assertThat(root.get("room").get("roomName").asText()).isEqualTo("جلسة العصف الذهني & Projet Démo");
        assertThat(root.get("room").get("ideas").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("CSV export starts with UTF-8 BOM, escapes quotes/commas, and neutralizes formula injection")
    void renderCsv_bomAndFormulaInjectionDefense() {
        BackendRoomExportDto room = createSampleRoom();
        String csv = exportService.renderCsv(room);

        // UTF-8 BOM check
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');

        // Header check
        assertThat(csv).contains("Idea ID,Category,Idea,Votes,Author,Comments Count,Created At");

        // Formula injection neutralization: values starting with =, +, -, @, \t, \r must be prefixed with single quote '
        assertThat(csv).contains("'=SUM(A1:B10)");
        assertThat(csv).doesNotContain(",=SUM(A1:B10)");

        // Arabic and French text in CSV
        assertThat(csv).contains("تصميم الواجهة");
        assertThat(csv).contains("Architecture & Conception");
    }

    @Test
    @DisplayName("escapeCsvField neutralizes formula injection characters (=, +, -, @, \\t, \\r)")
    void escapeCsvField_neutralizesInjection() {
        assertThat(SessionExportService.escapeCsvField("=cmd|'/C calc'!A0")).isEqualTo("'=cmd|'/C calc'!A0");
        assertThat(SessionExportService.escapeCsvField("+12345")).isEqualTo("'+12345");
        assertThat(SessionExportService.escapeCsvField("-2000")).isEqualTo("'-2000");
        assertThat(SessionExportService.escapeCsvField("@evil")).isEqualTo("'@evil");
        assertThat(SessionExportService.escapeCsvField("@SUM(1,2)")).isEqualTo("\"'@SUM(1,2)\"");
        assertThat(SessionExportService.escapeCsvField("\tTabInjected")).isEqualTo("'\tTabInjected");
        assertThat(SessionExportService.escapeCsvField("\rReturnInjected")).isEqualTo("\"'\rReturnInjected\"");
        assertThat(SessionExportService.escapeCsvField("Normal Text")).isEqualTo("Normal Text");
        assertThat(SessionExportService.escapeCsvField("Text with, comma and \"quotes\""))
                .isEqualTo("\"Text with, comma and \"\"quotes\"\"\"");
    }

    @Test
    @DisplayName("sanitizeFilename strips illegal characters, quotes, CRLF, and collapses whitespace")
    void sanitizeFilename_stripsDangerousChars() {
        assertThat(SessionExportService.sanitizeFilename("../../etc/passwd", "fallback"))
                .isEqualTo("etcpasswd");
        assertThat(SessionExportService.sanitizeFilename("My Room: \"Brainstorming\" <2026>?", "fallback"))
                .isEqualTo("My-Room-Brainstorming-2026");
        assertThat(SessionExportService.sanitizeFilename("   \r\n\t   ", "default-fallback"))
                .isEqualTo("default-fallback");
        assertThat(SessionExportService.sanitizeFilename("Safe-Filename_123", "fallback"))
                .isEqualTo("Safe-Filename_123");
    }

    @Test
    @DisplayName("Invalid export format throws InvalidExportFormatException")
    void invalidFormat_throwsException() {
        BackendRoomExportDto room = createSampleRoom();
        assertThatThrownBy(() -> exportService.renderExport("Title", "Notes", null, room, "exe"))
                .isInstanceOf(InvalidExportFormatException.class)
                .hasMessageContaining("Unsupported export format");
    }

    @Test
    @DisplayName("renderExport creates RFC-5987 and standard Content-Disposition header")
    void renderExport_contentDispositionHeaders() {
        BackendRoomExportDto room = createSampleRoom();
        var result = exportService.renderExport("Sprint 3 Final", "Notes", null, room, "md");

        assertThat(result.filename()).isEqualTo("Sprint-3-Final.md");
        assertThat(result.mediaType()).isEqualTo(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        String disposition = result.headers().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull();
        assertThat(disposition).contains("filename=\"Sprint-3-Final.md\"");
        assertThat(disposition).contains("filename*=UTF-8''Sprint-3-Final.md");
    }
}
