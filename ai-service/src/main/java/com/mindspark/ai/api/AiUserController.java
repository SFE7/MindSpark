package com.mindspark.ai.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.ai.api.dto.AiUsageResponse;
import com.mindspark.ai.api.dto.AiUserResponse;
import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.quota.QuotaService;
import com.mindspark.ai.tier.Tier;
import com.mindspark.ai.tier.TierResolver;

@RestController
@RequestMapping("/api/ai")
public class AiUserController {

    private final TierResolver tierResolver;
    private final AiTiersProperties properties;
    private final QuotaService quotaService;
    private final Clock clock;

    public AiUserController(
            TierResolver tierResolver,
            AiTiersProperties properties,
            QuotaService quotaService,
            Clock clock) {
        this.tierResolver = tierResolver;
        this.properties = properties;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    @GetMapping("/me")
    public ResponseEntity<AiUserResponse> getMe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = extractUserId(authentication);
        Tier tier = tierResolver.resolve(authentication.getAuthorities());
        return ResponseEntity.ok(new AiUserResponse(userId, tier));
    }

    @GetMapping("/usage")
    public ResponseEntity<AiUsageResponse> getUsage(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = extractUserId(authentication);
        Tier tier = tierResolver.resolve(authentication.getAuthorities());
        int dailyLimit = properties.getTierConfig(tier).getDailyRequestLimit();
        int usedToday = quotaService.getUsageToday(userId);
        int remainingToday = Math.max(0, dailyLimit - usedToday);

        LocalDate today = LocalDate.now(clock);
        String resetsAtUtc = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        return ResponseEntity.ok(new AiUsageResponse(tier, dailyLimit, usedToday, remainingToday, resetsAtUtc));
    }

    private String extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken().getSubject();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else {
            return authentication.getName();
        }
    }
}
