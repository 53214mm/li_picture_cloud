package com.li.lipicturecloud.application.companion.observation.view;

import java.time.Instant;

/** 喂养观测列表使用的易读摘要。 */
public record CompanionFeedRunSummaryView(
        Long runId,
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
        String requestedPolicyLabel,
        String actualNutritionLabel,
        boolean degraded,
        int attemptCount,
        long durationMillis,
        Instant createTime,
        Instant updateTime) {
}
