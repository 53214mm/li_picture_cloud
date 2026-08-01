package com.li.lipicturecloud.collaboration.event;

import com.li.lipicturecloud.collaboration.model.CollaborationEvent;

public record CollaborationEventEnvelope(
        String eventId,
        String sourceInstanceId,
        Long pictureId,
        CollaborationEvent event
) {
}
