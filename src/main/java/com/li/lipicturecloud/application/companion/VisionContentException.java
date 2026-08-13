package com.li.lipicturecloud.application.companion;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 视觉取图阶段可安全记录和分类的失败。
 *
 * <p>异常消息不能放对象 URL、图片字节或 COS 返回内容；后续适配器只按 {@link #safeCode()} 决定
 * 是否允许显式降级。</p>
 */
public final class VisionContentException extends RuntimeException implements VisionSafeFailure {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]{3,80}");

    private final String safeCode;

    public VisionContentException(String safeCode, String safeMessage) {
        super(safeMessage);
        this.safeCode = validateCode(safeCode);
    }

    @Override
    public String safeCode() {
        return safeCode;
    }

    private static String validateCode(String value) {
        Objects.requireNonNull(value, "safeCode");
        if (!SAFE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("safeCode must use upper snake case");
        }
        return value;
    }
}
