package com.li.lipicturecloud.domain.companion;

/**
 * 一次成长实际采用的营养来源模式。
 *
 * 新代码优先通过 {@link NutritionProvenance#actualMode()} 读取带审计信息的真实来源。
 */
public enum NutritionMode {
    /** 只按确定性演示规则生成，未读取图片内容。 */
    DEMO_DETERMINISTIC,
    /** 只使用尺寸、格式等元数据，未读取像素。 */
    METADATA_DETERMINISTIC,
    /** 实际向视觉供应商发送图片内容并获得结构化观察。 */
    VISUAL_MODEL
}
