package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次图片营养分析实际发生了什么。
 *
 * <p>这是成长记录的审计事实：即使请求的是视觉策略，最终也可能因安全降级而成为元数据营养。
 * 它不保存图片字节、Data URL 或模型原始响应，因而可安全地持久化并返回给用户。</p>
 */
public record NutritionProvenance(
        NutritionMode actualMode,
        boolean contentUnderstood,
        String providerCode,
        String modelCode,
        String promptVersion,
        String resultSchemaVersion,
        BigDecimal confidence,
        String fallbackReasonCode) {

    private static final Pattern CODE = Pattern.compile("[a-zA-Z0-9._-]{1,128}");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public NutritionProvenance {
        Objects.requireNonNull(actualMode, "actualMode");
        requireCode(providerCode, "providerCode");
        requireCode(modelCode, "modelCode");
        requireCode(promptVersion, "promptVersion");
        requireCode(resultSchemaVersion, "resultSchemaVersion");
        if (fallbackReasonCode != null) {
            requireCode(fallbackReasonCode, "fallbackReasonCode");
        }
        switch (actualMode) {
            case VISUAL_MODEL -> {
                if (!contentUnderstood) {
                    throw new IllegalArgumentException("visual nutrition must understand picture content");
                }
                if (confidence == null || confidence.compareTo(ZERO) < 0 || confidence.compareTo(ONE) > 0) {
                    throw new IllegalArgumentException("visual nutrition confidence must be in [0, 1]");
                }
                if (fallbackReasonCode != null) {
                    throw new IllegalArgumentException("visual nutrition cannot carry a fallback reason");
                }
            }
            case DEMO_DETERMINISTIC, METADATA_DETERMINISTIC -> {
                if (contentUnderstood) {
                    throw new IllegalArgumentException("non-visual nutrition cannot claim content understanding");
                }
                if (confidence != null) {
                    throw new IllegalArgumentException("non-visual nutrition cannot carry confidence");
                }
                if (actualMode == NutritionMode.DEMO_DETERMINISTIC && fallbackReasonCode != null) {
                    throw new IllegalArgumentException("demo nutrition cannot carry a fallback reason");
                }
            }
        }
    }

    public static NutritionProvenance demo() {
        return new NutritionProvenance(NutritionMode.DEMO_DETERMINISTIC, false,
                "internal", "demo-v1", "none", "nutrition-v1", null, null);
    }

    public static NutritionProvenance metadata() {
        return new NutritionProvenance(NutritionMode.METADATA_DETERMINISTIC, false,
                "internal", "metadata-v1", "none", "nutrition-v1", null, null);
    }

    public static NutritionProvenance metadataFallback(String reasonCode) {
        return new NutritionProvenance(NutritionMode.METADATA_DETERMINISTIC, false,
                "internal", "metadata-v1", "none", "nutrition-v1", null, reasonCode);
    }

    public static NutritionProvenance visual(String providerCode, String modelCode,
                                             String promptVersion, String resultSchemaVersion,
                                             BigDecimal confidence) {
        return new NutritionProvenance(NutritionMode.VISUAL_MODEL, true, providerCode, modelCode,
                promptVersion, resultSchemaVersion, confidence, null);
    }

    private static void requireCode(String value, String name) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + CODE.pattern());
        }
    }
}
