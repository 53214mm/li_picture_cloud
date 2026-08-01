package com.li.lipicturecloud.collaboration.event;

import com.li.lipicturecloud.collaboration.model.CollaborationEvent;

public interface CollaborationEventPublisher {
    void publish(Long pictureId, CollaborationEvent event);
}
