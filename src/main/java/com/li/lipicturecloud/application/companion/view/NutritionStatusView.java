package com.li.lipicturecloud.application.companion.view;

/**
 * 当前环境将如何请求图片营养。
 *
 * <p>这是<strong>请求策略</strong>而不是最近一次分析的结论：实际是否理解内容、实际模型及是否
 * 降级，都必须读取 {@link GrowthRecordView} 的不可变成长事实。</p>
 */
public record NutritionStatusView(String policy, String providerCode, String modelCode, int dailyLimit,
                                  String notice) {
}
