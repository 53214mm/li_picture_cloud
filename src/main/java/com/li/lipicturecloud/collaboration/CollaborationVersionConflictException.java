package com.li.lipicturecloud.collaboration;

public class CollaborationVersionConflictException extends RuntimeException {

    public CollaborationVersionConflictException(long expected, long actual) {
        super("协同版本冲突：客户端版本 " + expected + "，服务端版本 " + actual);
    }
}
