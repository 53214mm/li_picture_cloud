package com.li.lipicturecloud.application.companion.view;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 成长记录对前端的安全投影。
 *
 * <p>除了实际成长增量，还披露真实营养来源、模型标识、置信度和降级原因；
 * 不包含供应商原始响应、Token 或图片临时 URL。</p>
 */
public record GrowthRecordView(Long id, Long sourcePictureId, String eventType,
                               long lifeExperienceDelta, CompanionTraitsView traitDelta,
                               Map<String, Long> skillExperienceDelta, String reason,
                               String balanceVersion, String nutritionMode,
                               boolean contentUnderstood, String providerCode, String modelCode,
                               BigDecimal confidence, String fallbackReasonCode,
                               String nutritionLabel, Instant createdTime) {
}
