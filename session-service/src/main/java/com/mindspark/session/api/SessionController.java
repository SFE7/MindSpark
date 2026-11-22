package com.mindspark.session.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.session.dto.CreateSnapshotRequest;
import com.mindspark.session.dto.SnapshotDetailResponse;
import com.mindspark.session.dto.SnapshotSummaryResponse;
import com.mindspark.session.security.JwtClaimsHelper;
import com.mindspark.session.service.SessionExportService;
import com.mindspark.session.service.SessionSnapshotService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionSnapshotService snapshotService;

    public SessionController(SessionSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/snapshots")
    public ResponseEntity<SnapshotDetailResponse> createSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSnapshotRequest request) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        String username = JwtClaimsHelper.getUsername(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        SnapshotDetailResponse response = snapshotService.createSnapshot(sub, username, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<SnapshotSummaryResponse>> listSnapshots(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String q) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        List<SnapshotSummaryResponse> snapshots = snapshotService.listSnapshots(sub, isAdmin, roomId, q);
        return ResponseEntity.ok(snapshots);
    }

    @GetMapping("/snapshots/{id}")
    public ResponseEntity<SnapshotDetailResponse> getSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        SnapshotDetailResponse response = snapshotService.getSnapshot(id, sub, isAdmin);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/snapshots/{id}")
    public ResponseEntity<Void> deleteSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        snapshotService.deleteSnapshot(id, sub, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/snapshots/{id}/export")
    public ResponseEntity<byte[]> exportSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "md") String format) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        SessionExportService.ExportResult export = snapshotService.exportSnapshot(id, sub, isAdmin, format);
        return new ResponseEntity<>(export.content(), export.headers(), HttpStatus.OK);
    }

    @GetMapping("/rooms/{roomId}/export")
    public ResponseEntity<byte[]> exportLiveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "md") String format) {

        String sub = JwtClaimsHelper.getUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        SessionExportService.ExportResult export = snapshotService.exportLiveRoom(roomId, sub, isAdmin, format);
        return new ResponseEntity<>(export.content(), export.headers(), HttpStatus.OK);
    }

    private boolean hasAdminRole(Jwt jwt) {
        if (jwt == null) return false;
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) return false;
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List<?> roles)) return false;
        for (Object r : roles) {
            if (r instanceof String s) {
                String norm = s.toUpperCase().replace("-", "_");
                if ("ADMIN".equals(norm) || "SUPER_ADMIN".equals(norm) || "ROLE_ADMIN".equals(norm) || "ROLE_SUPER_ADMIN".equals(norm)) {
                    return true;
                }
            }
        }
        return false;
    }
}
