package com.mindspark.ai.tier;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class TierResolver {

    private static final String ROLE_PREMIUM_LOWER = "ROLE_premium";
    private static final String ROLE_PREMIUM_UPPER = "ROLE_PREMIUM";
    private static final String REALM_ROLE_PREMIUM = "premium";

    /**
     * Resolves user tier from granted authorities.
     * Rule: token contains the 'premium' realm role -> PREMIUM;
     * anything else (including no role at all, or only 'free') -> FREE.
     */
    public Tier resolve(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Tier.FREE;
        }

        boolean isPremium = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null && !auth.isBlank())
                .anyMatch(auth -> ROLE_PREMIUM_LOWER.equalsIgnoreCase(auth)
                        || ROLE_PREMIUM_UPPER.equalsIgnoreCase(auth)
                        || REALM_ROLE_PREMIUM.equalsIgnoreCase(auth));

        return isPremium ? Tier.PREMIUM : Tier.FREE;
    }
}
