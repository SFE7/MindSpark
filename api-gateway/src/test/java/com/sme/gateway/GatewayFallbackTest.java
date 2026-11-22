package com.sme.gateway;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayFallbackTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("Verify /fallback/ai-service returns 503 Service Unavailable with fallback=true JSON")
    void testFallbackAiService() {
        webTestClient.get()
                .uri("/fallback/ai-service")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.service").isEqualTo("ai-service")
                .jsonPath("$.fallback").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Service is temporarily unavailable. Please retry shortly.");
    }

    @Test
    @DisplayName("Verify /fallback/backend returns 503 Service Unavailable with fallback=true JSON")
    void testFallbackBackend() {
        webTestClient.get()
                .uri("/fallback/backend")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.service").isEqualTo("backend")
                .jsonPath("$.fallback").isEqualTo(true);
    }

    @Test
    @DisplayName("CRITICAL: Verify collaboration-websocket-service (/ws/**) route has NO filters (untouched WebSocket upgrade)")
    void testWebSocketRouteHasNoFilters() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        Route wsRoute = routes.stream()
                .filter(r -> "collaboration-websocket-service".equals(r.getId()))
                .findFirst()
                .orElse(null);

        assertThat(wsRoute).isNotNull();
        assertThat(wsRoute.getUri().toString()).isEqualTo("lb:ws://mindspark-backend");
        assertThat(wsRoute.getFilters()).isEmpty();
    }
}
