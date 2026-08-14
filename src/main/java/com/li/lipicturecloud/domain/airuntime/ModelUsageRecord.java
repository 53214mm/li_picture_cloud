package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次模型调用的追加式使用记录：只存安全字段，不存提示词、响应正文或 Token。
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
        Instant createdTime) {

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
    }

    public static ModelUsageRecord success(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, String correlationId, Instant now) {
        return new ModelUsageRecord(null, subjectId, task, connectionId, provider, modelCode,
                costSource, true, null, correlationId, now);
    }

    public static ModelUsageRecord failure(long subjectId, ModelTask task, Long connectionId,
                                           ModelProvider provider, String modelCode,
                                           CostSource costSource, String safeErrorCode,
                                           String correlationId, Instant now) {
        return new ModelUsageRecord(null, subjectId, task, connectionId, provider, modelCode,
                costSource, false, safeErrorCode, correlationId, now);
    }

    public ModelUsageRecord withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new ModelUsageRecord(persistedId, subjectId, task, connectionId, provider,
                modelCode, costSource, success, safeErrorCode, correlationId, createdTime);
    }
}
