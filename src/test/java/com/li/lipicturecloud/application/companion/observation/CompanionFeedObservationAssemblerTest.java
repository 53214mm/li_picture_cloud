package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunDetailView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionFeedObservationAssemblerTest {

    private static final Instant CREATED = Instant.parse("2026-08-30T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-08-30T10:00:03Z");

    private final CompanionFeedObservationAssembler assembler = new CompanionFeedObservationAssembler();

    @Test
    void completedRunExplainsAllStagesAndGrowth() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.COMPLETED.name(), null, null,
                "METADATA_ONLY", "METADATA_DETERMINISTIC", null, null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().statusLabel()).isEqualTo("已完成");
        assertThat(detail.summary().stageLabel()).isEqualTo("成长结算");
        assertThat(detail.summary().summary()).contains("成功");
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS");
    }

    @Test
    void fallbackCompletionExplainsThatVisualAnalysisDegraded() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.COMPLETED.name(), null, null,
                "VISUAL_WITH_METADATA_FALLBACK", "METADATA_DETERMINISTIC", "VISION_TIMEOUT", null);

        CompanionFeedRunSummaryView summary = assembler.summary(row, UPDATED);

        assertThat(summary.actualNutritionLabel()).contains("元数据降级");
        assertThat(summary.summary()).contains("降级");
        assertThat(summary.degraded()).isTrue();
    }

    @Test
    void authorizationFailureMarksDownstreamStagesAsSkipped() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.REJECTED.name(), "PICTURE_UNAVAILABLE",
                "图片不可用或无权访问", "METADATA_ONLY", null, null, null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().stageLabel()).isEqualTo("图片授权");
        assertThat(detail.summary().summary()).contains("授权");
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "FAILED", "SKIPPED", "SKIPPED");
    }

    @Test
    void nutritionFailureIdentifiesAnalysisAndDoesNotClaimGrowth() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.FAILED.name(), "NUTRITION_FAILED",
                "本次没有消化成功，图片未被消耗", "VISUAL_WITH_METADATA_FALLBACK", null, null, null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().stageLabel()).isEqualTo("营养分析");
        assertThat(detail.summary().summary()).contains("营养分析");
        assertThat(detail.growth()).isNull();
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "FAILED", "SKIPPED");
    }

    @Test
    void settlementFailureExplainsRollback() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.FAILED.name(), "FEED_COMMIT_FAILED",
                "本次没有消化成功，图片未被消耗", "METADATA_ONLY", null, null, null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().stageLabel()).isEqualTo("成长结算");
        assertThat(detail.summary().summary()).contains("回滚");
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "FAILED");
    }

    @Test
    void completedRunWithRetainedErrorIsShownAsRetrySuccess() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.COMPLETED.name(), "NUTRITION_FAILED",
                "本次没有消化成功，图片未被消耗", "METADATA_ONLY", "METADATA_DETERMINISTIC", null, null, 2);

        CompanionFeedRunSummaryView summary = assembler.summary(row, UPDATED);

        assertThat(summary.statusLabel()).isEqualTo("已完成");
        assertThat(summary.summary()).contains("重试").contains("最终成功");
        assertThat(summary.safeErrorCode()).isEqualTo("NUTRITION_FAILED");
    }

    @Test
    void processingRunDoesNotInventCurrentStage() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.PROCESSING.name(), null, null,
                "METADATA_ONLY", null, null, null);

        CompanionFeedRunSummaryView summary = assembler.summary(row, UPDATED);

        assertThat(summary.statusLabel()).isEqualTo("处理中");
        assertThat(summary.stageLabel()).isEqualTo("处理中");
        assertThat(summary.summary()).contains("无法确定");

        assertThat(assembler.detail(row, UPDATED).timeline()).extracting("status")
                .containsExactly("SUCCESS", "UNKNOWN", "UNKNOWN", "UNKNOWN");
    }

    @Test
    void authorizationServiceFailureStopsAtAuthorization() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.FAILED.name(), "AUTHORIZATION_CHECK_FAILED",
                "暂时无法校验图片访问权限，请重试", "METADATA_ONLY", null, null, null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().stageLabel()).isEqualTo("图片授权");
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "FAILED", "SKIPPED", "SKIPPED");
    }

    @Test
    void unknownStatusAndErrorStayUnknownInsteadOfBeingMisclassified() {
        CompanionFeedObservationRow unknownStatus = row("NEW_STATUS", null, null,
                "METADATA_ONLY", null, null, null);
        CompanionFeedObservationRow unknownError = row(FeedingRunStatus.FAILED.name(), "NEW_FAILURE_CODE",
                "发生了未知失败", "METADATA_ONLY", null, null, null);

        CompanionFeedRunDetailView statusDetail = assembler.detail(unknownStatus, UPDATED);
        CompanionFeedRunDetailView errorDetail = assembler.detail(unknownError, UPDATED);

        assertThat(statusDetail.summary().statusLabel()).isEqualTo("未知状态");
        assertThat(statusDetail.summary().stageLabel()).isEqualTo("未知阶段");
        assertThat(statusDetail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "UNKNOWN", "UNKNOWN", "UNKNOWN");
        assertThat(errorDetail.summary().stageLabel()).isEqualTo("未知阶段");
        assertThat(errorDetail.summary().summary()).contains("无法定位具体阶段");
    }

    @Test
    void familiarPictureUsesTheHistoricalNutritionMode() {
        CompanionFeedObservationRow row = row(FeedingRunStatus.COMPLETED.name(), null, null,
                "VISUAL_WITH_METADATA_FALLBACK", "METADATA_DETERMINISTIC", "SKIPPED_FAMILIAR", null);

        CompanionFeedRunDetailView detail = assembler.detail(row, UPDATED);

        assertThat(detail.summary().actualNutritionLabel()).contains("熟悉度");
        assertThat(detail.summary().degraded()).isFalse();
        assertThat(detail.timeline()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS");
    }

    private CompanionFeedObservationRow row(String status, String errorCode, String errorMessage,
                                            String requestedPolicy, String actualNutritionMode,
                                            String fallbackReasonCode, String pictureName) {
        return row(status, errorCode, errorMessage, requestedPolicy, actualNutritionMode,
                fallbackReasonCode, pictureName, 1);
    }

    private CompanionFeedObservationRow row(String status, String errorCode, String errorMessage,
                                            String requestedPolicy, String actualNutritionMode,
                                            String fallbackReasonCode, String pictureName, int attemptCount) {
        return new CompanionFeedObservationRow(
                21L, "fef53056-2d9f-467d-9b1d-1afe9a6638fe", 11L, 7L, 102L,
                "alice", "Alice", status, requestedPolicy, "dashscope", "qwen3.6-flash",
                31L, errorCode, errorMessage, errorCode == null ? null : UPDATED,
                attemptCount, 0L, CREATED, UPDATED, pictureName,
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                actualNutritionMode, actualNutritionMode != null && "VISUAL_MODEL".equals(actualNutritionMode),
                actualNutritionMode == null ? null : "internal", actualNutritionMode == null ? null : "metadata-v1",
                null, null, null, fallbackReasonCode, 42L, "PICTURE_FED", "演示/元数据营养", UPDATED);
    }
}
