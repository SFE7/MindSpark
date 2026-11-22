package com.mindspark.ai.tier;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierResolverTest {

    private TierResolver tierResolver;

    @BeforeEach
    void setUp() {
        tierResolver = new TierResolver();
    }

    @Test
    @DisplayName("User with 'premium' role (ROLE_premium) resolves to PREMIUM")
    void testPremiumRoleResolvesToPremium() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_premium")));
        assertEquals(Tier.PREMIUM, tier);
    }

    @Test
    @DisplayName("User with uppercase 'ROLE_PREMIUM' resolves to PREMIUM")
    void testUppercaseRolePremiumResolvesToPremium() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_PREMIUM")));
        assertEquals(Tier.PREMIUM, tier);
    }

    @Test
    @DisplayName("User with raw 'premium' role resolves to PREMIUM")
    void testRawPremiumRoleResolvesToPremium() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("premium")));
        assertEquals(Tier.PREMIUM, tier);
    }

    @Test
    @DisplayName("User with only 'free' role (ROLE_free) resolves to FREE")
    void testOnlyFreeRoleResolvesToFree() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_free")));
        assertEquals(Tier.FREE, tier);
    }

    @Test
    @DisplayName("User with only 'ROLE_FREE' resolves to FREE")
    void testUppercaseRoleFreeResolvesToFree() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_FREE")));
        assertEquals(Tier.FREE, tier);
    }

    @Test
    @DisplayName("User with empty roles list resolves to FREE")
    void testEmptyRolesResolvesToFree() {
        Tier tier = tierResolver.resolve(Collections.emptyList());
        assertEquals(Tier.FREE, tier);
    }

    @Test
    @DisplayName("Null authorities collection resolves to FREE")
    void testNullAuthoritiesResolvesToFree() {
        Tier tier = tierResolver.resolve(null);
        assertEquals(Tier.FREE, tier);
    }

    @Test
    @DisplayName("User with unrelated role (e.g. ROLE_USER) resolves to FREE")
    void testUnrelatedRoleResolvesToFree() {
        Tier tier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertEquals(Tier.FREE, tier);
    }

    @Test
    @DisplayName("User with both 'free' and 'premium' roles resolves to PREMIUM")
    void testBothFreeAndPremiumResolvesToPremium() {
        Tier tier = tierResolver.resolve(List.of(
                new SimpleGrantedAuthority("ROLE_free"),
                new SimpleGrantedAuthority("ROLE_premium")
        ));
        assertEquals(Tier.PREMIUM, tier);
    }

    @Test
    @DisplayName("Exact matching: role 'premium-trial' does not substring-match and resolves to FREE")
    void testPremiumTrialRoleResolvesToFree() {
        Tier rawTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("premium-trial")));
        assertEquals(Tier.FREE, rawTier, "Expected FREE for raw role 'premium-trial'");

        Tier roleTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_premium_trial")));
        assertEquals(Tier.FREE, roleTier, "Expected FREE for authority 'ROLE_premium_trial'");
    }

    @Test
    @DisplayName("Exact matching: role 'non-premium' does not substring-match and resolves to FREE")
    void testNonPremiumRoleResolvesToFree() {
        Tier rawTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("non-premium")));
        assertEquals(Tier.FREE, rawTier, "Expected FREE for raw role 'non-premium'");

        Tier roleTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_non_premium")));
        assertEquals(Tier.FREE, roleTier, "Expected FREE for authority 'ROLE_non_premium'");
    }

    @Test
    @DisplayName("Exact matching: role 'PREMIUM' resolves to PREMIUM")
    void testExactUppercasePremiumResolvesToPremium() {
        Tier rawTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("PREMIUM")));
        assertEquals(Tier.PREMIUM, rawTier, "Expected PREMIUM for raw role 'PREMIUM'");

        Tier roleTier = tierResolver.resolve(List.of(new SimpleGrantedAuthority("ROLE_PREMIUM")));
        assertEquals(Tier.PREMIUM, roleTier, "Expected PREMIUM for authority 'ROLE_PREMIUM'");
    }
}
