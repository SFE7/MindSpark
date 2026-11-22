package com.mindspark.media.api;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mindspark.media.client.BackendRoomClient;
import com.mindspark.media.dto.MediaMetadata;
import com.mindspark.media.dto.MediaUploadResponse;
import com.mindspark.media.dto.RoomAccessResponse;
import com.mindspark.media.security.JwtClaimsHelper;
import com.mindspark.media.service.MediaStorageService;
import com.mindspark.media.validation.ImageBinaryValidator;
import com.mindspark.media.validation.ImageBinaryValidator.ValidatedImage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final BackendRoomClient backendRoomClient;
    private final ImageBinaryValidator imageBinaryValidator;
    private final MediaStorageService mediaStorageService;

    public MediaController(
            BackendRoomClient backendRoomClient,
            ImageBinaryValidator imageBinaryValidator,
            MediaStorageService mediaStorageService) {
        this.backendRoomClient = backendRoomClient;
        this.imageBinaryValidator = imageBinaryValidator;
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private boolean isAdmin(Jwt jwt) {
        if (jwt == null) return false;
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return false;
        var roles = realmAccess.get("roles");
        if (roles instanceof Iterable<?> iterable) {
            for (Object r : iterable) {
                if (r instanceof String s) {
                    String norm = s.toUpperCase().replace("-", "_");
                    if ("ROLE_ADMIN".equals(norm) || "ADMIN".equals(norm)
                            || "ROLE_SUPER_ADMIN".equals(norm) || "SUPER_ADMIN".equals(norm)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @PostMapping(value = "/rooms/{roomId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadResponse> uploadRoomImage(
            @PathVariable String roomId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        mediaStorageService.validateIdentifiers(roomId, null);

        String callerSub = JwtClaimsHelper.getUserId(jwt);
        boolean admin = isAdmin(jwt);

        RoomAccessResponse access = backendRoomClient.fetchRoomAccess(roomId, callerSub);
        if (access == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND");
        }

        if (access.roomClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOM_CLOSED");
        }

        if (!admin && !access.isMember() && !access.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        byte[] bytes = file.getBytes();
        ValidatedImage validatedImage = imageBinaryValidator.validateAndSanitize(bytes);

        MediaUploadResponse response = mediaStorageService.storeImage(roomId, callerSub, validatedImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/rooms/{roomId}/{mediaId}")
    public ResponseEntity<Resource> getRoomImage(
            @PathVariable String roomId,
            @PathVariable String mediaId,
            @AuthenticationPrincipal Jwt jwt) {

        mediaStorageService.validateIdentifiers(roomId, mediaId);

        String callerSub = JwtClaimsHelper.getUserId(jwt);
        boolean admin = isAdmin(jwt);

        RoomAccessResponse access = backendRoomClient.fetchRoomAccess(roomId, callerSub);
        if (access == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND");
        }

        if (!admin && !access.isMember() && !access.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        MediaMetadata metadata = mediaStorageService.loadMetadata(roomId, mediaId);
        Resource resource = mediaStorageService.loadMediaFile(roomId, mediaId, metadata.extension());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, metadata.contentType())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    @DeleteMapping("/rooms/{roomId}/{mediaId}")
    public ResponseEntity<Void> deleteRoomImage(
            @PathVariable String roomId,
            @PathVariable String mediaId,
            @AuthenticationPrincipal Jwt jwt) {

        mediaStorageService.validateIdentifiers(roomId, mediaId);

        String callerSub = JwtClaimsHelper.getUserId(jwt);
        boolean admin = isAdmin(jwt);

        RoomAccessResponse access = backendRoomClient.fetchRoomAccess(roomId, callerSub);
        if (access == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND");
        }

        MediaMetadata metadata = mediaStorageService.loadMetadata(roomId, mediaId);

        boolean isUploader = metadata.uploaderSub() != null && metadata.uploaderSub().equals(callerSub);
        boolean isOwner = access.isOwner();

        if (!admin && !isOwner && !isUploader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        mediaStorageService.deleteMedia(roomId, mediaId, metadata.extension());
        return ResponseEntity.noContent().build();
    }
}
