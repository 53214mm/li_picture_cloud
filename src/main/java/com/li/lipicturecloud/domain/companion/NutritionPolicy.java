package com.li.lipicturecloud.domain.companion;

import java.util.Objects;

/**
 * 用户为一次喂养选择的分析策略。
 *
 * <p>策略描述的是系统<strong>允许</strong>采用的路径；实际采用的模型、版本和降级原因
 * 由 {@link NutritionProvenance} 在分析完成后如实记录。两者刻意分离，避免在模型调用前
 * 把“已理解图片内容”写入可重放的喂养运行。</p>
 */
public enum NutritionPolicy {
    DEMO_ONLY,
    METADATA_ONLY,
    VISUAL_WITH_METADATA_FALLBACK;

    public boolean accepts(NutritionProvenance provenance) {
        Objects.requireNonNull(provenance, "provenance");
        return switch (this) {
            case DEMO_ONLY -> provenance.actualMode() == NutritionMode.DEMO_DETERMINISTIC;
            case METADATA_ONLY -> provenance.actualMode() == NutritionMode.METADATA_DETERMINISTIC
                    && provenance.fallbackReasonCode() == null;
            case VISUAL_WITH_METADATA_FALLBACK -> provenance.actualMode() == NutritionMode.VISUAL_MODEL
                    || provenance.actualMode() == NutritionMode.METADATA_DETERMINISTIC
                    && provenance.fallbackReasonCode() != null;
        };
    }

    /** Converts the two pre-vision configured analyzers into explicit policies. */
    public static NutritionPolicy fromLegacyMode(NutritionMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case DEMO_DETERMINISTIC -> DEMO_ONLY;
            case METADATA_DETERMINISTIC -> METADATA_ONLY;
            case VISUAL_MODEL -> VISUAL_WITH_METADATA_FALLBACK;
        };
    }
}
