package com.mindspark.session.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.mindspark.session.dto.BackendCategoryDto;
import com.mindspark.session.dto.BackendCommentDto;
import com.mindspark.session.dto.BackendIdeaDto;
import com.mindspark.session.dto.BackendRoomExportDto;
import com.mindspark.session.exception.InvalidExportFormatException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Service
public class SessionExportService {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("UTC"));

    public SessionExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ExportResult(
            byte[] content,
            MediaType mediaType,
            String filename,
            HttpHeaders headers
    ) {}

    public ExportResult renderExport(
            String title,
            String notes,
            String aiSummary,
            BackendRoomExportDto roomData,
            String format) {

        String safeFormat = format != null ? format.trim().toLowerCase() : "";
        String baseTitle = title != null && !title.isBlank() ? title : (roomData != null ? roomData.roomName() : "session");
        String safeBaseName = sanitizeFilename(baseTitle, "mindspark-session");

        return switch (safeFormat) {
            case "md", "markdown" -> {
                String md = renderMarkdown(baseTitle, notes, aiSummary, roomData);
                String filename = safeBaseName + ".md";
                MediaType mt = new MediaType("text", "markdown", StandardCharsets.UTF_8);
                yield new ExportResult(md.getBytes(StandardCharsets.UTF_8), mt, filename, createHeaders(filename, mt));
            }
            case "json" -> {
                String json = renderJson(baseTitle, notes, aiSummary, roomData);
                String filename = safeBaseName + ".json";
                MediaType mt = new MediaType("application", "json", StandardCharsets.UTF_8);
                yield new ExportResult(json.getBytes(StandardCharsets.UTF_8), mt, filename, createHeaders(filename, mt));
            }
            case "csv" -> {
                String csv = renderCsv(roomData);
                String filename = safeBaseName + ".csv";
                MediaType mt = new MediaType("text", "csv", StandardCharsets.UTF_8);
                yield new ExportResult(csv.getBytes(StandardCharsets.UTF_8), mt, filename, createHeaders(filename, mt));
            }
            default -> throw new InvalidExportFormatException("Unsupported export format: " + format + ". Allowed formats: md, json, csv");
        };
    }

    public String renderMarkdown(String title, String notes, String aiSummary, BackendRoomExportDto room) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(title).append("\n\n");

        if (room != null) {
            sb.append("**Room:** ").append(room.roomName());
            if (room.invitationCode() != null && !room.invitationCode().isBlank()) {
                sb.append(" (`").append(room.invitationCode()).append("`)");
            }
            sb.append("\n");

            if (room.createdAt() != null) {
                sb.append("**Created:** ").append(DATE_FMT.format(room.createdAt())).append(" UTC\n");
            }

            int ideaCount = room.ideas() != null ? room.ideas().size() : 0;
            int categoryCount = room.categories() != null ? room.categories().size() : 0;
            int totalVotes = room.ideas() != null ? room.ideas().stream().mapToInt(BackendIdeaDto::voteCount).sum() : 0;
            int participantCount = room.participants() != null ? room.participants().size() : 0;

            sb.append("**Summary:** ").append(ideaCount).append(" ideas · ")
                    .append(totalVotes).append(" votes · ")
                    .append(categoryCount).append(" categories · ")
                    .append(participantCount).append(" participants\n\n");
        }

        if (notes != null && !notes.isBlank()) {
            sb.append("## 📝 Session Notes\n\n");
            sb.append(notes.trim()).append("\n\n");
        }

        if (aiSummary != null && !aiSummary.isBlank()) {
            sb.append("## ✨ AI Summary & Action Plan\n\n");
            sb.append(aiSummary.trim()).append("\n\n");
        }

        if (room != null && room.ideas() != null && !room.ideas().isEmpty()) {
            // Top Voted Ideas (top 5 or all if fewer)
            List<BackendIdeaDto> sortedIdeas = new ArrayList<>(room.ideas());
            sortedIdeas.sort((a, b) -> Integer.compare(b.voteCount(), a.voteCount()));

            sb.append("## 🏆 Top Voted Ideas\n\n");
            sb.append("| Rank | Idea | Category | Votes | Author |\n");
            sb.append("| :--- | :--- | :--- | :---: | :--- |\n");
            int rank = 1;
            for (BackendIdeaDto idea : sortedIdeas) {
                if (rank > 5 && idea.voteCount() == 0) break;
                String ideaText = escapeMarkdownTable(idea.title() != null && !idea.title().isBlank() ? idea.title() : idea.text());
                String category = escapeMarkdownTable(idea.categoryName() != null ? idea.categoryName() : "Uncategorized");
                String author = escapeMarkdownTable(idea.authorName() != null ? idea.authorName() : "Anonymous");
                sb.append("| ").append(rank++).append(" | ")
                        .append(ideaText).append(" | ")
                        .append(category).append(" | ")
                        .append(idea.voteCount()).append(" | ")
                        .append(author).append(" |\n");
            }
            sb.append("\n");

            // Group ideas by category
            Map<Long, List<BackendIdeaDto>> categorized = new LinkedHashMap<>();
            List<BackendIdeaDto> uncategorized = new ArrayList<>();

            for (BackendIdeaDto idea : room.ideas()) {
                if (idea.categoryId() != null) {
                    categorized.computeIfAbsent(idea.categoryId(), k -> new ArrayList<>()).add(idea);
                } else {
                    uncategorized.add(idea);
                }
            }

            sb.append("## 📁 Ideas by Category\n\n");
            if (room.categories() != null) {
                for (BackendCategoryDto cat : room.categories()) {
                    List<BackendIdeaDto> catIdeas = categorized.getOrDefault(cat.id(), Collections.emptyList());
                    sb.append("### ").append(cat.name()).append(" (").append(catIdeas.size()).append(")\n\n");
                    if (catIdeas.isEmpty()) {
                        sb.append("*No ideas in this category.*\n\n");
                    } else {
                        renderIdeasMarkdown(sb, catIdeas);
                    }
                }
            }

            if (!uncategorized.isEmpty()) {
                sb.append("### Uncategorized (").append(uncategorized.size()).append(")\n\n");
                renderIdeasMarkdown(sb, uncategorized);
            }
        }

        return sb.toString();
    }

    private void renderIdeasMarkdown(StringBuilder sb, List<BackendIdeaDto> ideas) {
        for (BackendIdeaDto idea : ideas) {
            String title = idea.title() != null && !idea.title().isBlank() ? idea.title() : idea.text();
            sb.append("- **").append(title).append("**");
            sb.append(" — *").append(idea.voteCount()).append(" votes*");
            if (idea.authorName() != null) {
                sb.append(" (by ").append(idea.authorName()).append(")");
            }
            sb.append("\n");

            if (idea.text() != null && !idea.text().equals(title) && !idea.text().isBlank()) {
                sb.append("  > ").append(idea.text().replace("\n", "\n  > ")).append("\n");
            }

            if (idea.comments() != null && !idea.comments().isEmpty()) {
                for (BackendCommentDto c : idea.comments()) {
                    sb.append("  - 💬 **").append(c.authorName() != null ? c.authorName() : "User").append("**: ")
                            .append(c.content()).append("\n");
                }
            }
        }
        sb.append("\n");
    }

    public String renderJson(String title, String notes, String aiSummary, BackendRoomExportDto room) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportTitle", title);
        export.put("exportedAt", DATE_FMT.format(java.time.Instant.now()) + " UTC");
        export.put("notes", notes != null ? notes : "");
        export.put("aiSummary", aiSummary != null ? aiSummary : "");
        export.put("room", room);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON export: " + e.getMessage(), e);
        }
    }

    public String renderCsv(BackendRoomExportDto room) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Excel Arabic / French support
        sb.append('\uFEFF');

        // CSV Header
        sb.append("Idea ID,Category,Idea,Votes,Author,Comments Count,Created At\n");

        if (room != null && room.ideas() != null) {
            for (BackendIdeaDto idea : room.ideas()) {
                String id = idea.id() != null ? idea.id().toString() : "";
                String category = idea.categoryName() != null ? idea.categoryName() : "Uncategorized";
                String ideaText = idea.title() != null && !idea.title().isBlank() ? idea.title() : (idea.text() != null ? idea.text() : "");
                String votes = String.valueOf(idea.voteCount());
                String author = idea.authorName() != null ? idea.authorName() : "Anonymous";
                String commentCount = String.valueOf(idea.comments() != null ? idea.comments().size() : 0);
                String createdAt = idea.createdAt() != null ? DATE_FMT.format(idea.createdAt()) : "";

                sb.append(escapeCsvField(id)).append(",")
                        .append(escapeCsvField(category)).append(",")
                        .append(escapeCsvField(ideaText)).append(",")
                        .append(escapeCsvField(votes)).append(",")
                        .append(escapeCsvField(author)).append(",")
                        .append(escapeCsvField(commentCount)).append(",")
                        .append(escapeCsvField(createdAt)).append("\n");
            }
        }

        return sb.toString();
    }

    public static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        String str = value;
        // Formula injection defense: any cell starting with =, +, -, @, \t, or \r
        if (!str.isEmpty()) {
            char first = str.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                str = "'" + str;
            }
        }
        // RFC-4180: if field contains comma, quote, or newline, escape quotes and enclose in quotes
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    public static String sanitizeFilename(String rawName, String fallback) {
        if (rawName == null || rawName.isBlank()) {
            return fallback;
        }
        // Remove relative path traversal, illegal filesystem characters, control characters
        String safe = rawName.replaceAll("\\.+", "")
                .replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (safe.isBlank()) {
            return fallback;
        }
        return safe;
    }

    private static HttpHeaders createHeaders(String filename, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        String safeFilename = filename.replace("\"", "").replace("\r", "").replace("\n", "");
        String encodedFilename = URLEncoder.encode(safeFilename, StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                safeFilename, encodedFilename);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        return headers;
    }

    private static String escapeMarkdownTable(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ").trim();
    }
}
