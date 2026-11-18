package com.sme.maintenance.websocket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoomBroadcastEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoomBroadcastEventListener.class);

    private final RoomWebSocketHandler webSocketHandler;

    public RoomBroadcastEventListener(RoomWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleIdeaChanged(IdeaChangedEvent event) {
        try {
            if ("idea-deleted".equals(event.type())) {
                Map<String, Object> payload = Map.of(
                        "type", "idea-deleted",
                        "id", event.ideaId().toString(),
                        "version", event.version()
                );
                webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
                return;
            }

            if (event.ideaData() == null) {
                return;
            }

            Map<String, Object> payload = Map.of(
                    "type", event.type(),
                    "version", event.version(),
                    "idea", event.ideaData()
            );
            webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
        } catch (Exception e) {
            log.error("Failed to broadcast IdeaChangedEvent for room {}: {}", event.roomId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCommentChanged(CommentChangedEvent event) {
        try {
            if ("comment-deleted".equals(event.type())) {
                Map<String, Object> payload = Map.of(
                        "type", "comment-deleted",
                        "ideaId", event.ideaId().toString(),
                        "commentId", event.commentId().toString(),
                        "version", event.version()
                );
                webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
                return;
            }

            if (event.commentData() == null) {
                return;
            }

            Map<String, Object> payload = Map.of(
                    "type", event.type(),
                    "ideaId", event.ideaId().toString(),
                    "version", event.version(),
                    "comment", event.commentData()
            );
            webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
        } catch (Exception e) {
            log.error("Failed to broadcast CommentChangedEvent for room {}: {}", event.roomId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDrawingChanged(DrawingChangedEvent event) {
        try {
            if ("drawing-deleted".equals(event.type())) {
                Map<String, Object> payload = Map.of(
                        "type", "drawing-deleted",
                        "id", event.drawingId().toString(),
                        "version", event.version()
                );
                webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
                return;
            }

            if (event.drawingData() == null) {
                return;
            }

            Map<String, Object> payload = Map.of(
                    "type", event.type(),
                    "version", event.version(),
                    "drawing", event.drawingData()
            );
            webSocketHandler.broadcastToRoom(event.roomId(), payload, null);
        } catch (Exception e) {
            log.error("Failed to broadcast DrawingChangedEvent for room {}: {}", event.roomId(), e.getMessage(), e);
        }
    }
}
