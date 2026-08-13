package com.li.lipicturecloud.application.companion.view;

import java.math.BigDecimal;

/**
 * 伙伴与主人之间的关系状态视图。
 */
public record CompanionRelationshipView(
        BigDecimal familiarity,
        BigDecimal trust,
        BigDecimal closeness,
        BigDecimal tacit,
        BigDecimal recentFeedback) {
}
