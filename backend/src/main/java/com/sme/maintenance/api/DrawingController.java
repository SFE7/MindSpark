package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.DrawingMoveRequest;
import com.sme.maintenance.api.ApiModels.DrawingRequest;
import com.sme.maintenance.api.ApiModels.DrawingResponse;
import com.sme.maintenance.drawing.DrawingService;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.services.AuthenticatedUserService;

@RestController
@RequestMapping("/api/rooms/{roomId}/drawings")
public class DrawingController {

    private final DrawingService drawingService;
    private final AuthenticatedUserService authenticatedUserService;

    public DrawingController(DrawingService drawingService, AuthenticatedUserService authenticatedUserService) {
        this.drawingService = drawingService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping
    public List<DrawingResponse> getDrawings(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        return drawingService.getRoomDrawings(roomId, user.getId());
    }

    @PostMapping
    public ResponseEntity<DrawingResponse> createDrawing(
            @PathVariable UUID roomId,
            @RequestBody DrawingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        DrawingResponse response = drawingService.createDrawing(roomId, user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{drawingId}")
    public DrawingResponse moveDrawing(
            @PathVariable UUID roomId,
            @PathVariable UUID drawingId,
            @RequestBody DrawingMoveRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        return drawingService.moveDrawing(roomId, drawingId, user.getId(), request);
    }

    @DeleteMapping("/{drawingId}")
    public ResponseEntity<Void> deleteDrawing(
            @PathVariable UUID roomId,
            @PathVariable UUID drawingId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        drawingService.deleteDrawing(roomId, drawingId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
