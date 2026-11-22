package com.sme.maintenance.websocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final RoomHandshakeInterceptor roomHandshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            RoomWebSocketHandler roomWebSocketHandler,
            RoomHandshakeInterceptor roomHandshakeInterceptor,
            @Value("${mindspark.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOrigins) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.roomHandshakeInterceptor = roomHandshakeInterceptor;

        List<String> patterns = new ArrayList<>();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(patterns::add);
        }
        // Always permit wildcard origin pattern so multi-PC collaboration on LAN never fails handshake
        if (!patterns.contains("*")) {
            patterns.add("*");
        }

        this.allowedOriginPatterns = patterns.toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/rooms/{roomId}", "/ws/rooms/*")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .addInterceptors(roomHandshakeInterceptor);
    }
}
