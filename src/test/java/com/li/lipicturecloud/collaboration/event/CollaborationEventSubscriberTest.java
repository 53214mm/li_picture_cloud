package com.li.lipicturecloud.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.model.CollaborationEvent;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import com.li.lipicturecloud.collaboration.websocket.CollaborationWebSocketHandler;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CollaborationEventSubscriberTest {

    @Test
    void forwardsRedisEnvelopeToOnlyTheLocalRoomBroadcaster() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CollaborationWebSocketHandler handler = mock(CollaborationWebSocketHandler.class);
        CollaborationEventSubscriber subscriber = new CollaborationEventSubscriber(objectMapper, handler);
        CollaborationEvent event = CollaborationEvent.state(new CollaborationState(7L, 90, 1.1, 2));
        CollaborationEventEnvelope envelope = new CollaborationEventEnvelope("event-1", "instance-a", 7L, event);

        subscriber.onMessage(objectMapper.writeValueAsString(envelope));

        verify(handler).broadcastLocal(7L, event);
    }
}
