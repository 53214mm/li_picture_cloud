package com.li.lipicturecloud.collaboration.model;

public record CollaborationState(
        Long pictureId,
        int rotation,
        double scale,
        long version
) {
    public static CollaborationState initial(Long pictureId) {
        return new CollaborationState(pictureId, 0, 1.0, 0);
    }
}
