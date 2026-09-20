package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;

/**
 * 一次模型调用的最小统一用量快照（记录紧跟模型调用结果）。
 *
 * <p>字段全部可空（供应商未返回时为空）：输入 token、输出 token、图片生成张数、
 * 供应商原始计量摘要（如 usage JSON 的短文本）。rawUsage 只保留安全摘要，
 * 剥离控制字符并截断到 200 字符；不记录提示词、响应正文或凭据。</p>
 */
public record ModelUsageSnapshot(
        Long inputTokens,
        Long outputTokens,
        Integer imageCount,
        String rawUsage) {

    private static final int MAX_RAW_USAGE_CODE_POINTS = 200;

    public ModelUsageSnapshot {
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must be nonnegative or null");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must be nonnegative or null");
        }
        if (imageCount != null && imageCount < 0) {
            throw new IllegalArgumentException("imageCount must be nonnegative or null");
        }
        if (rawUsage != null) {
            String normalized = rawUsage.strip();
            if (normalized.isEmpty()) {
                rawUsage = null;
            } else {
                // 供应商原始计量只保留安全摘要：去除控制字符并截断到 200 码点。
                normalized = normalized.codePoints()
                        .filter(codePoint -> !Character.isISOControl(codePoint))
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();
                rawUsage = normalized.isBlank() ? null : truncate(normalized, MAX_RAW_USAGE_CODE_POINTS);
            }
        }
    }

    /** 供应商未返回任何用量信息时的空快照。 */
    public static ModelUsageSnapshot none() {
        return new ModelUsageSnapshot(null, null, null, null);
    }

    /** 图片生成类调用：至少记录生成张数。 */
    public static ModelUsageSnapshot images(int imageCount) {
        if (imageCount <= 0) {
            throw new IllegalArgumentException("imageCount must be positive");
        }
        return new ModelUsageSnapshot(null, null, imageCount, null);
    }

    private static String truncate(String value, int maxCodePoints) {
        Objects.requireNonNull(value, "value");
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }
}
