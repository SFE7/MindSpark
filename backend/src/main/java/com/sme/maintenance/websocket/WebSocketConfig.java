package com.sme.maintenance.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                           @Value("${mindspark.websocket.allowed-origins:http://localhost:4200,http://127.0.0.1:4200,http://localhost:3000}") String allowedOrigins) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/rooms")
                .setAllowedOrigins(allowedOrigins);
    }
}
