package com.mindspark.session.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.mindspark.session.dto.BackendRoomExportDto;
import com.mindspark.session.exception.RoomNotFoundException;
import com.mindspark.session.exception.RoomSourceUnavailableException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BackendRoomClient {

    private final RestClient restClient;
    private final String internalSecret;

    public BackendRoomClient(
            RestClient backendRestClient,
            @Value("${mindspark.internal.service-secret:mindspark-internal-dev-key}") String internalSecret) {
        this.restClient = backendRestClient;
        this.internalSecret = internalSecret;
    }

    @CircuitBreaker(name = "backendRoomExport", fallbackMethod = "fetchRoomExportFallback")
    public BackendRoomExportDto fetchRoomExportData(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID is required");
        }

        try {
            return restClient.get()
                    .uri("/internal/rooms/{roomId}/export-data", roomId)
                    .header("X-Internal-Service-Key", internalSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            throw new RoomNotFoundException("Room not found: " + roomId);
                        }
                        log.warn("Client error fetching room data from backend for roomId {}: {}", roomId, resp.getStatusCode());
                        throw new RoomSourceUnavailableException("Backend service rejected request with status: " + resp.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("Server error fetching room data from backend for roomId {}: {}", roomId, resp.getStatusCode());
                        throw new RoomSourceUnavailableException("Backend service temporarily unavailable (status " + resp.getStatusCode() + ")");
                    })
                    .body(BackendRoomExportDto.class);
        } catch (RoomNotFoundException e) {
            throw e;
        } catch (RoomSourceUnavailableException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        } catch (ResourceAccessException e) {
            log.error("Failed to connect to backend for room {}: {}", roomId, e.getMessage());
            throw new RoomSourceUnavailableException("Backend service unreachable. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error fetching room data for room {}: {}", roomId, e.getMessage(), e);
            throw new RoomSourceUnavailableException("Failed to retrieve authoritative room data from backend.");
        }
    }

    public BackendRoomExportDto fetchRoomExportFallback(String roomId, Throwable t) {
        if (t instanceof RoomNotFoundException rnf) {
            throw rnf;
        }
        if (t instanceof IllegalArgumentException iae) {
            throw iae;
        }
        log.warn("Circuit breaker backendRoomExport fallback triggered for roomId {}: {}", roomId, t.getMessage());
        throw new RoomSourceUnavailableException("Backend service is temporarily unavailable. Please retry shortly.");
    }
}
