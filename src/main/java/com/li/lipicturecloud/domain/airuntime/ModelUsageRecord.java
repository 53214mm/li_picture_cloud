package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次模型调用的追加式使用记录：只存安全字段，不存提示词、响应正文或凭据 Token。
 * 用量以 {@link ModelUsageSnapshot} 记录（输入/输出 token、图片张数、供应商原始计量）。
 */
public record ModelUsageRecord(
        Long id,
        long subjectId,
        ModelTask task,
        Long connectionId,
        ModelProvider provider,
        String modelCode,
        CostSource costSource,
        boolean success,
        String safeErrorCode,
        String correlationId,
        Instant createdTime,
        Long inputTokens,
        Long outputTokens,
        Integer imageCount,
        String rawUsage) {

    private static final Pattern CODE = Pattern.compile("[a-zA-Z0-9._\\-]{1,64}");

    public ModelUsageRecord {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0) {
            throw new IllegalArgumentException("invalid usage identity");
        }
        Objects.requireNonNull(task, "task");
        if (connectionId != null && connectionId <= 0) {
            throw new IllegalArgumentException("connectionId must be positive or null");
        }
        Objects.requireNonNull(provider, "provider");
        if (modelCode == null || !CODE.matcher(modelCode).matches()) {
            throw new IllegalArgumentException("modelCode must match " + CODE.pattern());
        }
        Objects.requireNonNull(costSource, "costSource");
        if (!success) {
            if (safeErrorCode == null || !CODE.matcher(safeErrorCode).matches()) {
                throw new IllegalArgumentException("failed usage requires a safe error code");
            }
        } else if (safeErrorCode != null) {
            throw new IllegalArgumentException("successful usage cannot carry an error code");
        }
        Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.length() != 36) {
            throw new IllegalArgumentException("correlationId must be a UUID string");
        }
        Objects.requireNonNull(createdTime, "createdTime");
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must be nonnegative or null");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must be nonnegative or null");
        }
        if (imageCount != null && imageCount < 0) {
            throw new IllegalArgumentException("imageCount must be nonnegative or null");
        }
        if (rawUsage != null && rawUsage.codePointCount(0, rawUsage.length()) > 200) {
            throw new IllegalArgumentException("rawUsage must not exceed 200 code points");
        }
    }

    public static ModelUsageRecord success(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, ModelUsageSnapshot usage,
                                           String correlationId, Instant now) {
        return new ModelUsageRecord(null, subjectId, task, connectionId, provider, modelCode,
                costSource, true, null, correlationId, now,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.imageCount(),
                usage == null ? null : usage.rawUsage());
    }

    /** 兼容便捷入口：无用量信息时等价于 {@link ModelUsageSnapshot#none()}。 */
    public static ModelUsageRecord success(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, String correlationId, Instant now) {
        return success(subjectId, task, connectionId, provider, modelCode, costSource,
                ModelUsageSnapshot.none(), correlationId, now);
    }

    public static ModelUsageRecord failure(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, ModelUsageSnapshot usage,
                                           String safeErrorCode, String correlationId, Instant now) {
        return new ModelUsageRecord(null, subjectId, task, connectionId, provider, modelCode,
                costSource, false, safeErrorCode, correlationId, now,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.imageCount(),
                usage == null ? null : usage.rawUsage());
    }

    /** 兼容便捷入口：无用量信息时等价于 {@link ModelUsageSnapshot#none()}。 */
    public static ModelUsageRecord failure(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, String safeErrorCode,
                                           String correlationId, Instant now) {
        return failure(subjectId, task, connectionId, provider, modelCode, costSource,
                ModelUsageSnapshot.none(), safeErrorCode, correlationId, now);
    }

    public ModelUsageRecord withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new ModelUsageRecord(persistedId, subjectId, task, connectionId, provider,
                modelCode, costSource, success, safeErrorCode, correlationId, createdTime,
                inputTokens, outputTokens, imageCount, rawUsage);
    }
}
