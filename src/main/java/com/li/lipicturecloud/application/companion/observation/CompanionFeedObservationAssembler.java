package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunDetailView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedStageView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedTechnicalView;
import com.li.lipicturecloud.application.companion.view.GrowthRecordView;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将已有喂养运行事实转换成管理员能够直接理解的阶段说明。
 *
 * <p>阶段时间线是基于现有状态和安全错误码推导的逻辑时间线，不会把没有持久化的
 * 中间时间伪装成精确耗时。</p>
 */
@Component
public class CompanionFeedObservationAssembler {

    public CompanionFeedRunSummaryView summary(CompanionFeedObservationRow row, Instant now) {
        return buildSummary(row, now);
    }

    public CompanionFeedRunDetailView detail(CompanionFeedObservationRow row, Instant now) {
        return detail(row, null, now);
    }

    public CompanionFeedRunDetailView detail(CompanionFeedObservationRow row,
                                             GrowthRecordView growth,
                                             Instant now) {
        CompanionFeedRunSummaryView summary = buildSummary(row, now);
        return new CompanionFeedRunDetailView(summary, timeline(row), growth, technical(row));
    }

    private CompanionFeedRunSummaryView buildSummary(CompanionFeedObservationRow row, Instant now) {
        FeedingRunStatus status = statusOf(row.status());
        boolean degraded = isDegraded(row.fallbackReasonCode());
        String stageCode = stageCode(status, row.safeErrorCode());
        String stageLabel = stageLabel(stageCode);
        String summary = summaryText(row, status, degraded);
        return new CompanionFeedRunSummaryView(
                row.runId(), row.correlationId(), row.subjectId(), row.userAccount(), row.userName(),
                row.pictureId(), pictureLabel(row.pictureName(), row.pictureId()), row.status(), statusLabel(status),
                stageCode, stageLabel, summary, row.requestedPolicy(), policyLabel(row.requestedPolicy()),
                row.actualNutritionMode(), nutritionLabel(row.actualNutritionMode(), row.fallbackReasonCode()),
                row.actualProviderCode(), row.actualModelCode(), row.contentUnderstood(), row.confidence(),
                row.fallbackReasonCode(), degraded, row.attemptCount(), durationMillis(row, now),
                row.createTime(), row.updateTime(), row.safeErrorCode(), row.safeErrorMessage(),
                row.safeErrorTime(), row.resultGrowthRecordId());
    }

    private List<CompanionFeedStageView> timeline(CompanionFeedObservationRow row) {
        FeedingRunStatus status = statusOf(row.status());
        String stage = stageCode(status, row.safeErrorCode());
        boolean stageUnknown = status == null || status == FeedingRunStatus.PROCESSING
                || "UNKNOWN".equals(stage);
        boolean degraded = isDegraded(row.fallbackReasonCode());
        boolean authorizationFailed = status == FeedingRunStatus.REJECTED
                || status == FeedingRunStatus.FAILED && "AUTHORIZATION".equals(stage);
        boolean nutritionFailed = status == FeedingRunStatus.FAILED && "NUTRITION".equals(stage);
        boolean settlementFailed = status == FeedingRunStatus.FAILED && "SETTLEMENT".equals(stage);
        boolean completed = status == FeedingRunStatus.COMPLETED;

        return List.of(
                new CompanionFeedStageView("RUN_CREATED", "SUCCESS", "运行创建",
                        "喂养请求已登记，可以用幂等键安全重试。", row.createTime()),
                new CompanionFeedStageView("AUTHORIZATION",
                        stageUnknown ? "UNKNOWN" : authorizationFailed ? "FAILED" : "SUCCESS",
                        "图片授权",
                        stageUnknown ? "当前记录没有保存精确阶段，暂时不能确认授权结果。"
                                : authorizationFailed ? errorDescription(row.safeErrorCode())
                                : "该次执行时，图片授权校验已通过。",
                        authorizationFailed ? row.safeErrorTime() : null),
                new CompanionFeedStageView("NUTRITION",
                        stageUnknown ? "UNKNOWN" : authorizationFailed ? "SKIPPED"
                                : nutritionFailed ? "FAILED" : completed && degraded ? "DEGRADED"
                                : completed || settlementFailed ? "SUCCESS" : "SKIPPED",
                        "营养分析", nutritionDescription(row, authorizationFailed, nutritionFailed, stageUnknown, degraded),
                        nutritionFailed ? row.safeErrorTime() : null),
                new CompanionFeedStageView("SETTLEMENT",
                        stageUnknown ? "UNKNOWN" : authorizationFailed || nutritionFailed ? "SKIPPED"
                                : settlementFailed ? "FAILED" : completed ? "SUCCESS" : "SKIPPED",
                        "成长结算", settlementDescription(status, settlementFailed, authorizationFailed,
                                nutritionFailed, stageUnknown),
                        settlementFailed || completed ? row.updateTime() : null)
        );
    }

    private CompanionFeedTechnicalView technical(CompanionFeedObservationRow row) {
        return new CompanionFeedTechnicalView(row.runId(), row.companionId(), row.subjectId(), row.pictureId(),
                row.resultGrowthRecordId(), row.correlationId(), row.idempotencyKey(), row.revision(),
                row.attemptCount(), row.requestedPolicy(), row.requestedProviderCode(), row.requestedModelCode(),
                row.actualNutritionMode(), row.contentUnderstood(), row.actualProviderCode(), row.actualModelCode(),
                row.promptVersion(), row.resultSchemaVersion(), row.fallbackReasonCode(), row.safeErrorCode(),
                row.safeErrorTime());
    }

    private static String summaryText(CompanionFeedObservationRow row, FeedingRunStatus status,
                                      boolean degraded) {
        if (status == FeedingRunStatus.COMPLETED) {
            String result = degraded
                    ? "喂养成功，但视觉分析未完成，已降级为元数据营养。"
                    : "喂养成功，授权、营养分析和成长结算均已完成。";
            if (row.attemptCount() > 1 && row.safeErrorCode() != null) {
                result += "之前曾重试失败，最终成功。";
            }
            return result;
        }
        if (status == FeedingRunStatus.REJECTED) {
            return "图片授权失败，营养分析和成长结算未执行。";
        }
        if (status == FeedingRunStatus.PROCESSING) {
            return "运行仍在处理中，当前记录无法确定具体阶段。";
        }
        if (status == null) {
            return "运行状态无法识别，未能确定失败阶段。";
        }
        if ("UNKNOWN".equals(stageCode(status, row.safeErrorCode()))) {
            return "运行失败，但当前安全错误信息无法定位具体阶段。";
        }
        if ("AUTHORIZATION".equals(stageCode(status, row.safeErrorCode()))) {
            return "图片授权阶段发生异常，后续阶段未执行。";
        }
        if ("SETTLEMENT".equals(stageCode(status, row.safeErrorCode()))) {
            return "成长结算失败，事务已回滚，伙伴数据未发生变化。";
        }
        return "营养分析失败，成长结算未执行，伙伴数据未发生变化。";
    }

    private static String nutritionDescription(CompanionFeedObservationRow row, boolean authorizationFailed,
                                               boolean nutritionFailed, boolean stageUnknown, boolean degraded) {
        if (stageUnknown) {
            return "当前记录无法确认营养分析是否开始或完成。";
        }
        if (authorizationFailed) {
            return "由于图片授权未通过，本阶段未执行。";
        }
        if (nutritionFailed) {
            return errorDescription(row.safeErrorCode());
        }
        if (degraded) {
            return "视觉分析未完成，已使用图片元数据继续生成营养。";
        }
        if ("SKIPPED_FAMILIAR".equals(row.fallbackReasonCode())) {
            return "图片已经喂养过，本次跳过视觉调用并计算熟悉度。";
        }
        return "已得到营养结果，等待或已经进入成长结算。";
    }

    private static String settlementDescription(FeedingRunStatus status, boolean failed,
                                                boolean authorizationFailed, boolean nutritionFailed,
                                                boolean stageUnknown) {
        if (stageUnknown) {
            return "当前记录无法确认成长结算是否执行。";
        }
        if (failed) {
            return "结算事务失败，伙伴、技能和成长记录均未生效。";
        }
        if (authorizationFailed || nutritionFailed) {
            return "由于上游阶段失败，本阶段未执行。";
        }
        if (status == FeedingRunStatus.PROCESSING) {
            return "等待营养结果完成后结算。";
        }
        if (status == FeedingRunStatus.COMPLETED) {
            return "伙伴、技能和成长记录已在同一事务中完成。";
        }
        return "本阶段未执行。";
    }

    private static String errorDescription(String code) {
        if (code == null) {
            return "发生了未分类的安全错误。";
        }
        return switch (code) {
            case "PICTURE_UNAVAILABLE" -> "图片不存在或当前用户无权访问。";
            case "AUTHORIZATION_CHECK_FAILED" -> "权限校验服务暂时异常。";
            case "VISION_TIMEOUT" -> "视觉模型响应超时。";
            case "VISION_RATE_LIMITED" -> "视觉服务触发限流。";
            case "VISION_UNAVAILABLE" -> "视觉服务当前不可用。";
            case "VISION_CREDENTIALS" -> "视觉服务凭据不可用。";
            case "VISION_INVALID_RESPONSE" -> "视觉模型返回格式无法解析。";
            case "VISION_IMAGE_TOO_LARGE" -> "图片超过视觉分析大小限制。";
            case "VISION_UNSUPPORTED_IMAGE_FORMAT" -> "图片格式不支持视觉分析。";
            case "VISION_IMAGE_UNAVAILABLE" -> "无法安全读取图片内容。";
            case "FEED_COMMIT_FAILED" -> "成长结算失败，事务已回滚。";
            case "NUTRITION_FAILED" -> "营养分析发生未分类异常。";
            default -> "喂养阶段发生安全错误。";
        };
    }

    private static String stageCode(FeedingRunStatus status, String errorCode) {
        if (status == null) {
            return "UNKNOWN";
        }
        if (status == FeedingRunStatus.COMPLETED) {
            return "SETTLEMENT";
        }
        if (status == FeedingRunStatus.REJECTED || "PICTURE_UNAVAILABLE".equals(errorCode)
                || "AUTHORIZATION_CHECK_FAILED".equals(errorCode)) {
            return "AUTHORIZATION";
        }
        if (status == FeedingRunStatus.PROCESSING) {
            return "PROCESSING";
        }
        if ("FEED_COMMIT_FAILED".equals(errorCode)) {
            return "SETTLEMENT";
        }
        if (status == FeedingRunStatus.FAILED && isNutritionErrorCode(errorCode)) {
            return "NUTRITION";
        }
        return "UNKNOWN";
    }

    private static boolean isNutritionErrorCode(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        return switch (errorCode) {
            case "NUTRITION_FAILED", "VISION_TIMEOUT", "VISION_RATE_LIMITED", "VISION_UNAVAILABLE",
                    "VISION_CREDENTIALS", "VISION_INVALID_RESPONSE", "VISION_IMAGE_TOO_LARGE",
                    "VISION_UNSUPPORTED_IMAGE_FORMAT", "VISION_IMAGE_UNAVAILABLE" -> true;
            default -> false;
        };
    }

    private static String stageLabel(String stageCode) {
        return switch (stageCode) {
            case "AUTHORIZATION" -> "图片授权";
            case "NUTRITION" -> "营养分析";
            case "SETTLEMENT" -> "成长结算";
            case "PROCESSING" -> "处理中";
            case "UNKNOWN" -> "未知阶段";
            default -> "未知阶段";
        };
    }

    private static String statusLabel(FeedingRunStatus status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case PROCESSING -> "处理中";
            case COMPLETED -> "已完成";
            case FAILED -> "失败";
            case REJECTED -> "已拒绝";
        };
    }

    private static String policyLabel(String policy) {
        if (policy == null) {
            return "未知策略";
        }
        return switch (policy) {
            case "DEMO_ONLY", "DEMO_DETERMINISTIC" -> "演示营养";
            case "METADATA_ONLY", "METADATA_DETERMINISTIC" -> "元数据营养";
            case "VISUAL_WITH_METADATA_FALLBACK" -> "视觉营养，可降级";
            default -> "未知策略";
        };
    }

    private static String nutritionLabel(String mode, String fallbackReasonCode) {
        if (mode == null) {
            return "尚无结果";
        }
        if ("VISUAL_MODEL".equals(mode)) {
            return "视觉模型分析";
        }
        if ("SKIPPED_FAMILIAR".equals(fallbackReasonCode)) {
            return "熟悉度分析（跳过视觉调用）";
        }
        if (fallbackReasonCode != null) {
            return "元数据降级";
        }
        return switch (mode) {
            case "METADATA_DETERMINISTIC" -> "元数据分析";
            case "DEMO_DETERMINISTIC" -> "演示营养";
            default -> "未知营养方式";
        };
    }

    private static boolean isDegraded(String fallbackReasonCode) {
        return fallbackReasonCode != null && !"SKIPPED_FAMILIAR".equals(fallbackReasonCode);
    }

    private static String pictureLabel(String pictureName, Long pictureId) {
        return pictureName == null || pictureName.isBlank() ? "图片 #" + pictureId : pictureName;
    }

    private static long durationMillis(CompanionFeedObservationRow row, Instant now) {
        FeedingRunStatus status = statusOf(row.status());
        Instant end = status == null || status == FeedingRunStatus.PROCESSING ? now : row.updateTime();
        return Math.max(0L, Duration.between(row.createTime(), end).toMillis());
    }

    private static FeedingRunStatus statusOf(String status) {
        try {
            return FeedingRunStatus.valueOf(status);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
