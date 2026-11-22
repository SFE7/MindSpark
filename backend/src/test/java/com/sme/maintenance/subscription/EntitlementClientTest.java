package com.sme.maintenance.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitlementClientTest {

    @Test
    @DisplayName("Production behavior (fallback-enabled=false): unreachable subscription-service throws 503 SubscriptionServiceUnavailableException")
    void testProductionFailClosedWhenServiceUnavailable() {
        // Point to non-routable port to guarantee connection failure
        EntitlementClient client = new EntitlementClient("http://127.0.0.1:59999", "test-secret");
        ReflectionTestUtils.setField(client, "fallbackEnabled", false);

        assertThatThrownBy(() -> client.getEntitlements("user-sub-123"))
                .isInstanceOf(SubscriptionServiceUnavailableException.class)
                .hasMessageContaining("Subscription service is temporarily unavailable");
    }

    @Test
    @DisplayName("Production behavior (fallback-enabled=false): null or blank sub throws SubscriptionServiceUnavailableException")
    void testProductionFailClosedWhenSubMissing() {
        EntitlementClient client = new EntitlementClient("http://127.0.0.1:59999", "test-secret");
        ReflectionTestUtils.setField(client, "fallbackEnabled", false);

        assertThatThrownBy(() -> client.getEntitlements(null))
                .isInstanceOf(SubscriptionServiceUnavailableException.class)
                .hasMessageContaining("Keycloak user ID (sub) is missing or blank");

        assertThatThrownBy(() -> client.getEntitlements("  "))
                .isInstanceOf(SubscriptionServiceUnavailableException.class)
                .hasMessageContaining("Keycloak user ID (sub) is missing or blank");
    }

    @Test
    @DisplayName("Test behavior (fallback-enabled=true): unreachable service safely returns default FREE plan")
    void testTestFallbackEnabledWhenServiceUnavailable() {
        EntitlementClient client = new EntitlementClient("http://127.0.0.1:59999", "test-secret");
        ReflectionTestUtils.setField(client, "fallbackEnabled", true);

        EntitlementDto result = client.getEntitlements("test-user-sub");
        assertThat(result).isNotNull();
        assertThat(result.planCode()).isEqualTo("FREE");
        assertThat(result.maxActiveRooms()).isEqualTo(3);
        assertThat(result.maxParticipantsPerRoom()).isEqualTo(5);
        assertThat(result.aiDailyLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("Test behavior (fallback-enabled=true): null sub returns default FREE plan")
    void testTestFallbackEnabledWhenSubNull() {
        EntitlementClient client = new EntitlementClient("http://127.0.0.1:59999", "test-secret");
        ReflectionTestUtils.setField(client, "fallbackEnabled", true);

        EntitlementDto result = client.getEntitlements(null);
        assertThat(result).isNotNull();
        assertThat(result.planCode()).isEqualTo("FREE");
        assertThat(result.maxActiveRooms()).isEqualTo(3);
    }
}
