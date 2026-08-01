package com.li.lipicturecloud.config;

import com.li.lipicturecloud.collaboration.websocket.CollaborationHandshakeInterceptor;
import com.li.lipicturecloud.collaboration.websocket.CollaborationWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CollaborationWebSocketConfig implements WebSocketConfigurer {

    private final CollaborationWebSocketHandler handler;
    private final CollaborationHandshakeInterceptor handshakeInterceptor;
    private final String[] allowedOrigins;

    public CollaborationWebSocketConfig(
            CollaborationWebSocketHandler handler,
            CollaborationHandshakeInterceptor handshakeInterceptor,
            @Value("${app.collaboration.allowed-origins:http://localhost:5173}") String[] allowedOrigins
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/collaboration")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}
