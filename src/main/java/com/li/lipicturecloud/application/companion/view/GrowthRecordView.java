package com.li.lipicturecloud.application.companion.view;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;

public record GrowthRecordView(Long id, Long sourcePictureId, String eventType,
                               long lifeExperienceDelta, CompanionTraitsView traitDelta,
                               Map<String, Long> skillExperienceDelta, String reason,
                               String balanceVersion, String nutritionMode,
                               boolean contentUnderstood, String providerCode, String modelCode,
                               BigDecimal confidence, String fallbackReasonCode,
                               String nutritionLabel, Instant createdTime) {
}
