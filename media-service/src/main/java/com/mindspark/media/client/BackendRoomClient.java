package com.mindspark.media.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.mindspark.media.dto.RoomAccessResponse;
import com.mindspark.media.exception.RoomForbiddenException;
import com.mindspark.media.exception.RoomNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

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

    @CircuitBreaker(name = "backendRoomAccess", fallbackMethod = "fetchRoomAccessFallback")
    public RoomAccessResponse fetchRoomAccess(String roomId, String callerSub) {
        if (roomId == null || roomId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROOM_ID_REQUIRED");
        }

        try {
            return restClient.get()
                    .uri("/internal/rooms/{roomId}/access/{callerSub}", roomId, callerSub != null ? callerSub : "anonymous")
                    .header("X-Internal-Service-Key", internalSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 404) {
                            throw new RoomNotFoundException("ROOM_NOT_FOUND");
                        }
                        if (code == 403) {
                            throw new RoomForbiddenException("FORBIDDEN");
                        }
                        log.warn("Backend rejected room access check for room {} sub {}: status {}", roomId, callerSub, code);
                        throw new ResponseStatusException(HttpStatus.valueOf(code), "BACKEND_REJECTED");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("Backend server error verifying room access for room {}: status {}", roomId, resp.getStatusCode());
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE");
                    })
                    .body(RoomAccessResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RoomNotFoundException("ROOM_NOT_FOUND");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new RoomForbiddenException("FORBIDDEN");
        } catch (ResourceAccessException e) {
            log.error("Failed to connect to backend for room {} access: {}", roomId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE");
        } catch (Exception e) {
            log.error("Unexpected error verifying room access for room {}: {}", roomId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE");
        }
    }

    public RoomAccessResponse fetchRoomAccessFallback(String roomId, String callerSub, Throwable t) {
        if (t instanceof RoomNotFoundException rnf) {
            throw rnf;
        }
        if (t instanceof RoomForbiddenException rfe) {
            throw rfe;
        }
        if (t instanceof ResponseStatusException rse && (rse.getStatusCode().value() == 404 || rse.getStatusCode().value() == 403)) {
            throw rse;
        }
        log.warn("Circuit breaker backendRoomAccess fallback triggered for roomId {} caller {}: {}", roomId, callerSub, t.getMessage());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE");
    }
}
