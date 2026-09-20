package com.li.lipicturecloud.application.companion.observation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 后台观测查询使用的只读投影行。
 *
 * <p>它不是喂养领域对象，也不参与任何状态迁移；查询层只把运行表和成功成长记录中
 * 已经存在的安全字段带到观测组装器。</p>
 */
public record CompanionFeedObservationRow(
        Long runId,
        String correlationId,
        Long companionId,
        Long subjectId,
        Long pictureId,
        String userAccount,
        String userName,
        String status,
        String requestedPolicy,
        String requestedProviderCode,
        String requestedModelCode,
        Long resultGrowthRecordId,
        String safeErrorCode,
        String safeErrorMessage,
        Instant safeErrorTime,
        Integer attemptCount,
        Long revision,
        Instant createTime,
        Instant updateTime,
        String pictureName,
        String idempotencyKey,
        String actualNutritionMode,
        Boolean contentUnderstood,
        String actualProviderCode,
        String actualModelCode,
        String promptVersion,
        String resultSchemaVersion,
        BigDecimal confidence,
        String fallbackReasonCode,
        Long growthLifeExperienceDelta,
        String growthEventType,
        String growthReason,
        Instant growthCreateTime) {

    public CompanionFeedObservationRow withPictureName(String name) {
        return new CompanionFeedObservationRow(runId, correlationId, companionId, subjectId, pictureId,
                userAccount, userName, status, requestedPolicy, requestedProviderCode, requestedModelCode,
                resultGrowthRecordId, safeErrorCode, safeErrorMessage, safeErrorTime, attemptCount, revision,
                createTime, updateTime, name, idempotencyKey, actualNutritionMode, contentUnderstood,
                actualProviderCode, actualModelCode, promptVersion, resultSchemaVersion, confidence,
                fallbackReasonCode, growthLifeExperienceDelta, growthEventType, growthReason, growthCreateTime);
    }
}
