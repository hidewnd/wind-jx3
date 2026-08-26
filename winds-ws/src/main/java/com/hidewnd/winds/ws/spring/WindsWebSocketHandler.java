package com.hidewnd.winds.ws.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.bot.huangli.event.HuangliUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WindsWebSocketHandler extends TextWebSocketHandler {

    private static final TextMessage CONNECTION_SUCCESS = new TextMessage(
            "{\"type\":\"connection.success\",\"message\":\"连接成功\"}");

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WindsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64 * 1024);
        sessions.put(session.getId(), concurrentSession);
        try {
            concurrentSession.sendMessage(CONNECTION_SUCCESS);
        } catch (IOException exception) {
            sessions.remove(session.getId());
            throw exception;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @EventListener
    public void broadcast(HuangliUpdatedEvent event) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("type", "huangli.updated")
                .put("date", event.date())
                .put("url", event.url());
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("黄历更新消息序列化失败", exception);
        }
        sessions.forEach((sessionId, session) -> send(sessionId, session, message));
    }

    private void send(String sessionId, WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            sessions.remove(sessionId);
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException exception) {
            sessions.remove(sessionId);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            log.warn("WebSocket 消息发送失败，sessionId={}", sessionId, exception);
        }
    }
}
