package com.li.lipicturecloud.collaboration.model;

public record CollaborationCommand(
        String commandId,
        Long pictureId,
        Long actorUserId,
        CollaborationOperation operation,
        long baseVersion
) {
}
