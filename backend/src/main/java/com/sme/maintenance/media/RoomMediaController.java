package com.sme.maintenance.media;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.media.dto.CanvasImageDto;
import com.sme.maintenance.media.dto.CreateCanvasImageRequest;
import com.sme.maintenance.media.dto.CreateIdeaAttachmentRequest;
import com.sme.maintenance.media.dto.IdeaAttachmentDto;
import com.sme.maintenance.media.dto.RoomMediaBundleDto;
import com.sme.maintenance.media.dto.UpdateCanvasImageRequest;
import com.sme.maintenance.services.AuthenticatedUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomMediaController {

    private final RoomMediaService roomMediaService;
    private final AuthenticatedUserService authenticatedUserService;

    public RoomMediaController(
            RoomMediaService roomMediaService,
            AuthenticatedUserService authenticatedUserService) {
        this.roomMediaService = roomMediaService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/attachments")
    public ResponseEntity<RoomMediaBundleDto> getRoomAttachments(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        return ResponseEntity.ok(roomMediaService.getRoomMediaBundle(roomId, user));
    }

    @PostMapping("/ideas/{ideaId}/attachments")
    public ResponseEntity<IdeaAttachmentDto> addIdeaAttachment(
            @PathVariable UUID roomId,
            @PathVariable UUID ideaId,
            @Valid @RequestBody CreateIdeaAttachmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        IdeaAttachmentDto response = roomMediaService.addIdeaAttachment(roomId, ideaId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/ideas/{ideaId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteIdeaAttachment(
            @PathVariable UUID roomId,
            @PathVariable UUID ideaId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        roomMediaService.deleteIdeaAttachment(roomId, ideaId, attachmentId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/canvas-images")
    public ResponseEntity<CanvasImageDto> addCanvasImage(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateCanvasImageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        CanvasImageDto response = roomMediaService.addCanvasImage(roomId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/canvas-images/{imageId}")
    public ResponseEntity<CanvasImageDto> updateCanvasImage(
            @PathVariable UUID roomId,
            @PathVariable UUID imageId,
            @Valid @RequestBody UpdateCanvasImageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        CanvasImageDto response = roomMediaService.updateCanvasImage(roomId, imageId, request, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/canvas-images/{imageId}")
    public ResponseEntity<Void> deleteCanvasImage(
            @PathVariable UUID roomId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        roomMediaService.deleteCanvasImage(roomId, imageId, user);
        return ResponseEntity.noContent().build();
    }
}
