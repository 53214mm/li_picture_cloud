package com.li.lipicturecloud.collaboration.model;

public record CollaborationCommandRequest(
        String commandId,
        CollaborationOperation operation,
        long baseVersion
) {
}
