package com.li.lipicturecloud.collaboration.model;

public record CollaborationEvent(
        String type,
        Long actorUserId,
        CollaborationOperation operation,
        CollaborationState state,
        String message
) {
    public static CollaborationEvent state(CollaborationState state) {
        return new CollaborationEvent("STATE", null, null, state, "已同步最新编辑状态");
    }

    public static CollaborationEvent operation(Long userId, CollaborationOperation operation,
                                                CollaborationState state) {
        return new CollaborationEvent("OPERATION", userId, operation, state,
                "用户 " + userId + " 执行了" + operationText(operation));
    }

    public static CollaborationEvent presence(String type, Long userId, CollaborationState state) {
        return new CollaborationEvent(type, userId, null, state,
                "JOIN".equals(type) ? "用户 " + userId + " 加入协同" : "用户 " + userId + " 离开协同");
    }

    public static CollaborationEvent error(String message, CollaborationState state) {
        return new CollaborationEvent("ERROR", null, null, state, message);
    }

    private static String operationText(CollaborationOperation operation) {
        return switch (operation) {
            case ROTATE_LEFT -> "左旋";
            case ROTATE_RIGHT -> "右旋";
            case ZOOM_IN -> "放大";
            case ZOOM_OUT -> "缩小";
        };
    }
}
