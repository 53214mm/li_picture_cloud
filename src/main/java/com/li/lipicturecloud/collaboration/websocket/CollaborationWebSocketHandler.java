package com.li.lipicturecloud.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.CollaborationSessionService;
import com.li.lipicturecloud.collaboration.CollaborationVersionConflictException;
import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationCommandRequest;
import com.li.lipicturecloud.collaboration.model.CollaborationEvent;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {

    private final CollaborationSessionService sessionService;
    private final SpaceAuthorizationAccessService accessService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public CollaborationWebSocketHandler(CollaborationSessionService sessionService,
                                         SpaceAuthorizationAccessService accessService,
                                         ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long pictureId = pictureId(session);
        Long userId = userId(session);
        rooms.computeIfAbsent(pictureId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, CollaborationEvent.state(sessionService.current(pictureId)));
        broadcast(pictureId, CollaborationEvent.presence(
                "JOIN", userId, sessionService.current(pictureId)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long pictureId = pictureId(session);
        Long userId = userId(session);
        try {
            accessService.checkForUser(
                    SpaceUserPermissionConstant.COLLABORATION_EDIT, pictureId, userId);
            CollaborationCommandRequest request = objectMapper.readValue(
                    message.getPayload(), CollaborationCommandRequest.class);
            CollaborationState state = sessionService.apply(new CollaborationCommand(
                    request.commandId(), pictureId, userId, request.operation(), request.baseVersion()));
            broadcast(pictureId, CollaborationEvent.operation(userId, request.operation(), state));
        } catch (CollaborationVersionConflictException exception) {
            send(session, CollaborationEvent.error(exception.getMessage(), sessionService.current(pictureId)));
        } catch (RuntimeException exception) {
            send(session, CollaborationEvent.error("协同操作被拒绝：" + exception.getMessage(),
                    sessionService.current(pictureId)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException {
        Long pictureId = pictureId(session);
        Set<WebSocketSession> room = rooms.get(pictureId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(pictureId, room);
            }
        }
        broadcast(pictureId, CollaborationEvent.presence(
                "LEAVE", userId(session), sessionService.current(pictureId)));
    }

    private void broadcast(Long pictureId, CollaborationEvent event) throws IOException {
        for (WebSocketSession session : rooms.getOrDefault(pictureId, Set.of())) {
            if (session.isOpen()) {
                send(session, event);
            }
        }
    }

    private void send(WebSocketSession session, CollaborationEvent event) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        }
    }

    private Long pictureId(WebSocketSession session) {
        return (Long) session.getAttributes().get(CollaborationHandshakeInterceptor.PICTURE_ID);
    }

    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(CollaborationHandshakeInterceptor.USER_ID);
    }
}
