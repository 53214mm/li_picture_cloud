package com.li.lipicturecloud.application.companion.observation.view;

import java.time.Instant;

/** 技术详情折叠区使用的安全字段，不包含请求体、URL、模型原文或凭据。 */
public record CompanionFeedTechnicalView(
        Long runId,
        Long companionId,
        Long subjectId,
        Long pictureId,
        Long resultGrowthRecordId,
        String correlationId,
        String idempotencyKey,
        long revision,
        int attemptCount,
        String requestedPolicy,
        String requestedProviderCode,
        String requestedModelCode,
        String actualNutritionMode,
        Boolean contentUnderstood,
        String actualProviderCode,
        String actualModelCode,
        String promptVersion,
        String resultSchemaVersion,
        String fallbackReasonCode,
        String safeErrorCode,
        Instant safeErrorTime) {
}
