package com.li.lipicturecloud.application.companion.view;

import java.time.Instant;

/**
 * 主动提案的展示视图。
 */
public record CompanionProposalView(
        Long id,
        String opportunityType,
        java.math.BigDecimal impulseScore,
        String content,
        String status,
        String gateResult,
        Instant createdTime) {
}
