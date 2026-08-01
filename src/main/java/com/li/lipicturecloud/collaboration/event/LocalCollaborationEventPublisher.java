package com.li.lipicturecloud.collaboration.event;

import com.li.lipicturecloud.collaboration.model.CollaborationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.collaboration.store", havingValue = "memory")
public class LocalCollaborationEventPublisher implements CollaborationEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final String instanceId = UUID.randomUUID().toString();

    public LocalCollaborationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Long pictureId, CollaborationEvent event) {
        publisher.publishEvent(new CollaborationEventEnvelope(
                UUID.randomUUID().toString(), instanceId, pictureId, event));
    }
}
