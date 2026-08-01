package com.li.lipicturecloud.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.websocket.CollaborationWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CollaborationEventSubscriber {

    private final ObjectMapper objectMapper;
    private final CollaborationWebSocketHandler handler;

    public CollaborationEventSubscriber(ObjectMapper objectMapper, CollaborationWebSocketHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    public void onMessage(String payload) {
        try {
            onEnvelope(objectMapper.readValue(payload, CollaborationEventEnvelope.class));
        } catch (Exception exception) {
            log.warn("丢弃无效的 Redis 协同事件", exception);
        }
    }

    @EventListener
    public void onEnvelope(CollaborationEventEnvelope envelope) {
        try {
            handler.broadcastLocal(envelope.pictureId(), envelope.event());
        } catch (Exception exception) {
            log.warn("向本机 WebSocket 房间广播协同事件失败, pictureId={}",
                    envelope.pictureId(), exception);
        }
    }
}
