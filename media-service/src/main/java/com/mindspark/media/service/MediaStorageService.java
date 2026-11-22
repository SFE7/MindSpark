package com.mindspark.media.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.media.dto.MediaMetadata;
import com.mindspark.media.dto.MediaUploadResponse;
import com.mindspark.media.validation.ImageBinaryValidator.ValidatedImage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MediaStorageService {

    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public MediaStorageService(
            @Value("${mindspark.media.storage-dir:/data/media}") String storageDir,
            ObjectMapper objectMapper) {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            log.error("Failed to initialize storage root directory {}: {}", storageRoot, e.getMessage());
        }
    }

    public void validateIdentifiers(String roomId, String mediaId) {
        if (roomId == null || !ROOM_ID_PATTERN.matcher(roomId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ROOM_ID");
        }
        if (mediaId != null && !MEDIA_ID_PATTERN.matcher(mediaId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ID");
        }
    }

    private Path resolveSafeRoomDir(String roomId) {
        validateIdentifiers(roomId, null);
        Path roomDir = storageRoot.resolve(roomId).normalize();
        if (!roomDir.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED");
        }
        return roomDir;
    }

    private Path resolveSafeFile(String roomId, String filename) {
        Path roomDir = resolveSafeRoomDir(roomId);
        Path targetFile = roomDir.resolve(filename).normalize();
        if (!targetFile.startsWith(roomDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED");
        }
        return targetFile;
    }

    public MediaUploadResponse storeImage(String roomId, String uploaderSub, ValidatedImage validatedImage) {
        validateIdentifiers(roomId, null);
        Path roomDir = resolveSafeRoomDir(roomId);

        try {
            Files.createDirectories(roomDir);
        } catch (IOException e) {
            log.error("Failed to create room media directory {}: {}", roomDir, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
        }

        String mediaId = UUID.randomUUID().toString();
        Path targetFile = resolveSafeFile(roomId, mediaId + "." + validatedImage.extension());
        Path tempFile = resolveSafeFile(roomId, mediaId + ".tmp");
        Path metaFile = resolveSafeFile(roomId, mediaId + ".meta.json");

        try {
            // Write image atomically
            Files.write(tempFile, validatedImage.sanitizedBytes());
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // Write metadata sidecar
            MediaMetadata metadata = new MediaMetadata(
                    mediaId,
                    roomId,
                    uploaderSub != null ? uploaderSub : "anonymous",
                    validatedImage.contentType(),
                    validatedImage.extension(),
                    validatedImage.width(),
                    validatedImage.height(),
                    validatedImage.sanitizedBytes().length,
                    Instant.now()
            );

            byte[] metaBytes = objectMapper.writeValueAsBytes(metadata);
            Path tempMeta = resolveSafeFile(roomId, mediaId + ".meta.tmp");
            Files.write(tempMeta, metaBytes);
            Files.move(tempMeta, metaFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            String url = "/api/media/rooms/" + roomId + "/" + mediaId;
            return new MediaUploadResponse(
                    mediaId,
                    roomId,
                    url,
                    validatedImage.contentType(),
                    validatedImage.width(),
                    validatedImage.height(),
                    validatedImage.sanitizedBytes().length
            );
        } catch (IOException e) {
            log.error("Failed to store media file {}: {}", mediaId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
        }
    }

    public MediaMetadata loadMetadata(String roomId, String mediaId) {
        validateIdentifiers(roomId, mediaId);
        Path metaFile = resolveSafeFile(roomId, mediaId + ".meta.json");
        if (!Files.exists(metaFile) || !Files.isRegularFile(metaFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        }

        try {
            byte[] bytes = Files.readAllBytes(metaFile);
            return objectMapper.readValue(bytes, MediaMetadata.class);
        } catch (Exception e) {
            log.error("Failed to read metadata for {}: {}", mediaId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        }
    }

    public Resource loadMediaFile(String roomId, String mediaId, String extension) {
        validateIdentifiers(roomId, mediaId);
        Path targetFile = resolveSafeFile(roomId, mediaId + "." + extension);
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        }

        try {
            byte[] bytes = Files.readAllBytes(targetFile);
            return new ByteArrayResource(bytes);
        } catch (IOException e) {
            log.error("Failed to read media file for {}: {}", mediaId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_READ_ERROR");
        }
    }

    public void deleteMedia(String roomId, String mediaId, String extension) {
        validateIdentifiers(roomId, mediaId);
        try {
            Path targetFile = resolveSafeFile(roomId, mediaId + "." + extension);
            Path metaFile = resolveSafeFile(roomId, mediaId + ".meta.json");
            Files.deleteIfExists(targetFile);
            Files.deleteIfExists(metaFile);
        } catch (IOException e) {
            log.warn("Failed to delete media {}: {}", mediaId, e.getMessage());
        }
    }
}
