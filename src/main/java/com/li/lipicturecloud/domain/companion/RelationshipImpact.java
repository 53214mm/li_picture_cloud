package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 一次喂养对关系状态的影响候选，最终幅度由 {@link CompanionRelationshipRules} 截断。
 */
public record RelationshipImpact(
        BigDecimal familiarity,
        BigDecimal trust,
        BigDecimal closeness,
        BigDecimal tacit,
        BigDecimal recentFeedback) {

    private static final BigDecimal LIMIT = new BigDecimal("100.00");

    public RelationshipImpact {
        familiarity = normalize(familiarity);
        trust = normalize(trust);
        closeness = normalize(closeness);
        tacit = normalize(tacit);
        recentFeedback = normalize(recentFeedback);
    }

    public static RelationshipImpact zero() {
        return new RelationshipImpact(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean isZero() {
        return familiarity.signum() == 0 && trust.signum() == 0 && closeness.signum() == 0
                && tacit.signum() == 0 && recentFeedback.signum() == 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "impact value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(LIMIT) > 0) {
            throw new IllegalArgumentException("relationship impact must be between -100.00 and 100.00");
        }
        return normalized;
    }
}
