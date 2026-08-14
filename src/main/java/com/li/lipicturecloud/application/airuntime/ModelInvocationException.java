package com.li.lipicturecloud.application.airuntime;

/**
 * 模型调用失败（语言/视觉/图像创作共用）。只携带安全错误码；
 * 实现与日志均不得携带请求正文、响应正文或凭据。
 */
public class ModelInvocationException extends RuntimeException {

    private final String safeErrorCode;

    public ModelInvocationException(String safeErrorCode, String message) {
        super(message);
        this.safeErrorCode = safeErrorCode;
    }

    public ModelInvocationException(String safeErrorCode, String message, Throwable cause) {
        super(message, cause);
        this.safeErrorCode = safeErrorCode;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }
}
