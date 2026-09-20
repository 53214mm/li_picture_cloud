package com.li.lipicturecloud.application.companion.observation.view;

import java.math.BigDecimal;
import java.time.Instant;

/** 喂养观测列表使用的易读摘要。 */
public record CompanionFeedRunSummaryView(
        Long runId,
        String correlationId,
        Long subjectId,
        String userAccount,
        String userName,
        Long pictureId,
        String pictureName,
        String status,
        String statusLabel,
        String stageCode,
        String stageLabel,
        String summary,
        String requestedPolicy,
        String requestedPolicyLabel,
        String actualNutritionMode,
        String actualNutritionLabel,
        String actualProviderCode,
        String actualModelCode,
        Boolean contentUnderstood,
        BigDecimal confidence,
        String fallbackReasonCode,
        boolean degraded,
        int attemptCount,
        long durationMillis,
        Instant createTime,
        Instant updateTime,
        String safeErrorCode,
        String safeErrorMessage,
        Instant safeErrorTime,
        Long resultGrowthRecordId) {
}
