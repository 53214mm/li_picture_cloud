package com.li.lipicturecloud.application.companion;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 外部视觉 Provider 的安全失败；不保存原始 HTTP 响应或底层异常。
 */
public final class VisionProviderException extends RuntimeException implements VisionSafeFailure {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]{3,80}");
    private final String safeCode;

    public VisionProviderException(String safeCode, String safeMessage) {
        super(safeMessage);
        Objects.requireNonNull(safeCode, "safeCode");
        if (!SAFE_CODE.matcher(safeCode).matches()) {
            throw new IllegalArgumentException("safeCode must use upper snake case");
        }
        this.safeCode = safeCode;
    }

    @Override
    public String safeCode() {
        return safeCode;
    }
}
