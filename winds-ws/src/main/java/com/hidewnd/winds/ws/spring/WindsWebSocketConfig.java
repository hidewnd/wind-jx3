package com.hidewnd.winds.ws.spring;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WindsWebSocketConfig implements WebSocketConfigurer {

    private final WindsWebSocketHandler handler;
    private final WsTokenHandshakeInterceptor tokenInterceptor;

    public WindsWebSocketConfig(
            WindsWebSocketHandler handler,
            WsTokenHandshakeInterceptor tokenInterceptor) {
        this.handler = handler;
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(tokenInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
