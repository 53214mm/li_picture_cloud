package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;

import java.time.Instant;

/**
 * 模型使用记录的安全展示视图：只有安全字段，无提示词、响应正文或 Token。
 */
public record ModelUsageRecordView(
        long id,
        ModelTask task,
        Long connectionId,
        ModelProvider provider,
        String modelCode,
        CostSource costSource,
        boolean success,
        String safeErrorCode,
        String correlationId,
        Instant createdTime) {

    public static ModelUsageRecordView of(ModelUsageRecord record) {
        return new ModelUsageRecordView(record.id(), record.task(), record.connectionId(),
                record.provider(), record.modelCode(), record.costSource(), record.success(),
                record.safeErrorCode(), record.correlationId(), record.createdTime());
    }
}
