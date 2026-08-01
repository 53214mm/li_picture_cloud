package com.li.lipicturecloud.domain.collaboration;

public class StaleCollaborationVersionException extends RuntimeException {
    private final long expectedVersion;
    private final long actualVersion;

    public StaleCollaborationVersionException(long expectedVersion, long actualVersion) {
        super("协同版本冲突，客户端版本=" + expectedVersion + "，服务端版本=" + actualVersion);
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}
