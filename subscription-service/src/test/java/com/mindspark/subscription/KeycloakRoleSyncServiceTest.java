package com.mindspark.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.mindspark.subscription.service.KeycloakRoleSyncService;
import com.mindspark.subscription.service.KeycloakRoleSyncService.RoleSyncStatus;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleSyncServiceTest {

    @Test
    @DisplayName("When admin client credentials are empty, returns SKIPPED_UNCONFIGURED gracefully without error")
    void testUnconfiguredGracefulSkip() {
        KeycloakRoleSyncService service = new KeycloakRoleSyncService(
                RestClient.builder(),
                "http://localhost:8180",
                "mindspark",
                "",
                ""
        );

        assertThat(service.isConfigured()).isFalse();

        RoleSyncStatus assignStatus = service.assignPremiumRole("user-test-123");
        assertThat(assignStatus).isEqualTo(RoleSyncStatus.SKIPPED_UNCONFIGURED);

        RoleSyncStatus removeStatus = service.removePremiumRole("user-test-123");
        assertThat(removeStatus).isEqualTo(RoleSyncStatus.SKIPPED_UNCONFIGURED);
    }
}
