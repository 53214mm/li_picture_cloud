package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecordRepository;
import com.li.lipicturecloud.domain.airuntime.ModelUsageSnapshot;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * 模型调用使用记录的追加服务。记录只含安全字段；correlationId 由服务生成，
 * 任何路径都不记录提示词、响应正文或 Token。
 */
@Service
public class ModelUsageService {

    private final ModelUsageRecordRepository usageRepository;
    private final Clock clock;

    public ModelUsageService(ModelUsageRecordRepository usageRepository, Clock clock) {
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    public ModelUsageRecord recordSuccess(long subjectId, ModelTask task, Long connectionId,
                                          ModelProvider provider, String modelCode,
                                          CostSource costSource) {
        return recordSuccess(subjectId, task, connectionId, provider, modelCode, costSource,
                ModelUsageSnapshot.none());
    }

    /** 记录成功调用并携带最小统一用量快照（紧跟模型调用结果，独立于后续任务状态）。 */
    public ModelUsageRecord recordSuccess(long subjectId, ModelTask task, Long connectionId,
                                          ModelProvider provider, String modelCode,
                                          CostSource costSource, ModelUsageSnapshot usage) {
        checkArguments(subjectId, task, provider, modelCode, costSource);
        return usageRepository.append(ModelUsageRecord.success(subjectId, task, connectionId,
                provider, modelCode, costSource, usage, UUID.randomUUID().toString(),
                clock.instant()));
    }

    public ModelUsageRecord recordFailure(long subjectId, ModelTask task, Long connectionId,
                                          ModelProvider provider, String modelCode,
                                          CostSource costSource, String safeErrorCode) {
        return recordFailure(subjectId, task, connectionId, provider, modelCode, costSource,
                ModelUsageSnapshot.none(), safeErrorCode);
    }

    /** 记录失败调用并携带最小统一用量快照（供应商若在失败前返回了部分用量）。 */
    public ModelUsageRecord recordFailure(long subjectId, ModelTask task, Long connectionId,
                                          ModelProvider provider, String modelCode,
                                          CostSource costSource, ModelUsageSnapshot usage,
                                          String safeErrorCode) {
        checkArguments(subjectId, task, provider, modelCode, costSource);
        Objects.requireNonNull(safeErrorCode, "safeErrorCode");
        return usageRepository.append(ModelUsageRecord.failure(subjectId, task, connectionId,
                provider, modelCode, costSource, usage, safeErrorCode,
                UUID.randomUUID().toString(), clock.instant()));
    }

    /** 最近使用记录（倒序），limit 由仓储钳制在 [1, 100]。 */
    public java.util.List<ModelUsageRecord> listRecent(long subjectId, int limit) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        return usageRepository.findRecent(subjectId, limit);
    }

    private static void checkArguments(long subjectId, ModelTask task, ModelProvider provider,
                                       String modelCode, CostSource costSource) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelCode, "modelCode");
        Objects.requireNonNull(costSource, "costSource");
    }
}
