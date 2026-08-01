package com.li.lipicturecloud.domain.collaboration;

public final class CollaborationSession {
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 4.0;
    private static final double SCALE_STEP = 0.1;

    private final Long pictureId;
    private int rotation;
    private double scale = 1.0;
    private long version;

    private CollaborationSession(Long pictureId) {
        if (pictureId == null || pictureId <= 0) {
            throw new IllegalArgumentException("图片 ID 无效");
        }
        this.pictureId = pictureId;
    }

    public static CollaborationSession start(Long pictureId) {
        return new CollaborationSession(pictureId);
    }

    public CollaborationSnapshot apply(CollaborationAction action, long baseVersion) {
        if (baseVersion != version) {
            throw new StaleCollaborationVersionException(baseVersion, version);
        }
        switch (action) {
            case ROTATE_LEFT -> rotation = Math.floorMod(rotation - 90, 360);
            case ROTATE_RIGHT -> rotation = Math.floorMod(rotation + 90, 360);
            case ZOOM_IN -> scale = Math.min(MAX_SCALE, round(scale + SCALE_STEP));
            case ZOOM_OUT -> scale = Math.max(MIN_SCALE, round(scale - SCALE_STEP));
        }
        version++;
        return snapshot();
    }

    public CollaborationSnapshot snapshot() {
        return new CollaborationSnapshot(pictureId, rotation, scale, version);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
