package com.sme.maintenance.votepool;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.services.AuthenticatedUserService;
import com.sme.maintenance.votepool.dto.CastVoteRequest;
import com.sme.maintenance.votepool.dto.CreateVotePoolRequest;
import com.sme.maintenance.votepool.dto.VotePoolResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/rooms/{roomId}/vote-pools")
public class VotePoolController {

    private final VotePoolService votePoolService;
    private final AuthenticatedUserService authenticatedUserService;

    public VotePoolController(
            VotePoolService votePoolService,
            AuthenticatedUserService authenticatedUserService) {
        this.votePoolService = votePoolService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping
    public ResponseEntity<VotePoolResponse> createVotePool(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateVotePoolRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        VotePoolResponse response = votePoolService.createVotePool(roomId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/active")
    public ResponseEntity<VotePoolResponse> getActivePool(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "false") boolean includeLatestClosed,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        VotePoolResponse response = votePoolService.getActiveOrLatestPool(roomId, includeLatestClosed, user);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{poolId}/votes")
    public ResponseEntity<VotePoolResponse> castVote(
            @PathVariable UUID roomId,
            @PathVariable UUID poolId,
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        VotePoolResponse response = votePoolService.castVote(roomId, poolId, request, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{poolId}/votes/{ideaId}")
    public ResponseEntity<VotePoolResponse> removeVote(
            @PathVariable UUID roomId,
            @PathVariable UUID poolId,
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        VotePoolResponse response = votePoolService.removeVote(roomId, poolId, ideaId, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{poolId}/close")
    public ResponseEntity<VotePoolResponse> closeVotePool(
            @PathVariable UUID roomId,
            @PathVariable UUID poolId,
            @AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserService.getCurrentUser(jwt);
        VotePoolResponse response = votePoolService.closeVotePool(roomId, poolId, user);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(VoteBudgetExceededException.class)
    public ResponseEntity<Map<String, Object>> handleVoteBudgetExceeded(VoteBudgetExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "VOTE_BUDGET_EXCEEDED",
                "remaining", ex.getRemaining(),
                "limit", ex.getLimit()
        ));
    }
}
