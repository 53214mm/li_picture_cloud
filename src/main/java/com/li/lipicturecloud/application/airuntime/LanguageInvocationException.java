package com.li.lipicturecloud.application.airuntime;

/**
 * 语言模型调用失败。只携带安全错误码；实现与日志均不得携带请求正文、响应正文或凭据。
 */
public class LanguageInvocationException extends RuntimeException {

    private final String safeErrorCode;

    public LanguageInvocationException(String safeErrorCode, String message) {
        super(message);
        this.safeErrorCode = safeErrorCode;
    }

    public LanguageInvocationException(String safeErrorCode, String message, Throwable cause) {
        super(message, cause);
        this.safeErrorCode = safeErrorCode;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }
}
