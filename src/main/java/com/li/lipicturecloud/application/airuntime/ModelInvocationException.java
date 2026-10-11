package com.li.lipicturecloud.application.airuntime;

/**
 * 模型调用失败（语言/视觉/图像创作共用）。只携带安全错误码；
 * 实现与日志均不得携带请求正文、响应正文或凭据。
 */
public class ModelInvocationException extends RuntimeException {

    public enum FailureKind { UNKNOWN, REQUEST_REJECTED, NETWORK, TIMEOUT, PROTOCOL }

    private final String safeErrorCode;
    private final FailureKind failureKind;
    private final Integer httpStatus;

    public ModelInvocationException(String safeErrorCode, String message) {
        this(safeErrorCode, message, null);
    }

    public ModelInvocationException(String safeErrorCode, String message, Throwable cause) {
        this(safeErrorCode, message, cause, FailureKind.UNKNOWN, null);
    }

    /** Metadata is limited to an enum and HTTP status; never an upstream body. */
    public ModelInvocationException(String safeErrorCode, String message, Throwable cause,
                                    FailureKind failureKind, Integer httpStatus) {
        super(message, cause);
        this.safeErrorCode = safeErrorCode;
        this.failureKind = java.util.Objects.requireNonNull(failureKind);
        this.httpStatus = httpStatus != null && httpStatus >= 400 && httpStatus <= 599 ? httpStatus : null;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }

    public FailureKind failureKind() { return failureKind; }
    public Integer httpStatus() { return httpStatus; }
}
