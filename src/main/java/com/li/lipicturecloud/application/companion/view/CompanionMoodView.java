package com.li.lipicturecloud.application.companion.view;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 伙伴当前情绪的最小展示视图。摘要由服务端生成，前端不自行解释数值。
 */
public record CompanionMoodView(
        BigDecimal energy,
        BigDecimal joy,
        BigDecimal loneliness,
        BigDecimal inspiration,
        BigDecimal irritation,
        String summary,
        Instant updatedAt) {
}
