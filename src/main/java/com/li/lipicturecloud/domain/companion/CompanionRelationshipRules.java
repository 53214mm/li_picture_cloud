package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 关系状态的数值规则：首次完整喂养与重复熟悉感的固定影响，以及单轴变化上限。
 */
public final class CompanionRelationshipRules {

    private static final CompanionRelationshipRules V1 = new CompanionRelationshipRules(
            new RelationshipImpact(
                    new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("1.00"),
                    new BigDecimal("1.00"), new BigDecimal("5.00")),
            new RelationshipImpact(
                    new BigDecimal("2.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("1.00"), new BigDecimal("2.00")),
            new BigDecimal("10.00"));

    private final RelationshipImpact fullFeedImpact;
    private final RelationshipImpact revisitImpact;
    private final BigDecimal maxImpact;

    private CompanionRelationshipRules(RelationshipImpact fullFeedImpact,
                                       RelationshipImpact revisitImpact,
                                       BigDecimal maxImpact) {
        this.fullFeedImpact = Objects.requireNonNull(fullFeedImpact, "fullFeedImpact");
        this.revisitImpact = Objects.requireNonNull(revisitImpact, "revisitImpact");
        this.maxImpact = Objects.requireNonNull(maxImpact, "maxImpact")
                .setScale(2, RoundingMode.HALF_UP);
        if (maxImpact.signum() <= 0) {
            throw new IllegalArgumentException("maxImpact must be positive");
        }
    }

    public static CompanionRelationshipRules v1() {
        return V1;
    }

    /** 首次完整喂养的影响。 */
    public RelationshipImpact fullFeedImpact() {
        return fullFeedImpact;
    }

    /** 重复图片熟悉感的影响。 */
    public RelationshipImpact revisitImpact() {
        return revisitImpact;
    }

    /** 一次事件对单轴关系的最大变化量。 */
    public BigDecimal maxImpact() {
        return maxImpact;
    }
}
