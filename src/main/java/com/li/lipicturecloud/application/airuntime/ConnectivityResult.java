package com.li.lipicturecloud.application.airuntime;

/**
 * 一次连接探测的结果。探测失败只携带安全错误码，绝不携带端点响应正文或凭据信息。
 */
public record ConnectivityResult(boolean reachable, String safeErrorCode) {

    public static final String CREDENTIAL_REJECTED = "CREDENTIAL_REJECTED";
    public static final String UPSTREAM_TIMEOUT = "UPSTREAM_TIMEOUT";
    public static final String UPSTREAM_ERROR = "UPSTREAM_ERROR";

    public ConnectivityResult {
        if (reachable) {
            if (safeErrorCode != null) {
                throw new IllegalArgumentException("reachable result cannot carry an error code");
            }
        } else if (safeErrorCode == null || safeErrorCode.isBlank()) {
            throw new IllegalArgumentException("failed result requires a safe error code");
        }
    }

    public static ConnectivityResult success() {
        return new ConnectivityResult(true, null);
    }

    public static ConnectivityResult failed(String safeErrorCode) {
        return new ConnectivityResult(false, safeErrorCode);
    }
}
