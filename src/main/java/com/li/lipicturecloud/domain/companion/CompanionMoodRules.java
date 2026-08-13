package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 情绪数值规则：每小时的衰减幅度与单次喂养的每轴影响上限。
 *
 * <p>情绪规则不参与成长审计，也不需要版本协商；调整数值时直接修改并同步文档即可。</p>
 */
public final class CompanionMoodRules {

    private static final CompanionMoodRules V1 = new CompanionMoodRules(
            new BigDecimal("5.00"), new BigDecimal("15.00"));

    private final BigDecimal decayPerHour;
    private final BigDecimal maxImpact;

    private CompanionMoodRules(BigDecimal decayPerHour, BigDecimal maxImpact) {
        this.decayPerHour = normalize(decayPerHour);
        this.maxImpact = normalize(maxImpact);
    }

    public static CompanionMoodRules v1() {
        return V1;
    }

    /** 每满 1 小时，每轴向 0 方向减少的强度。 */
    public BigDecimal decayPerHour() {
        return decayPerHour;
    }

    /** 一次喂养对单轴情绪的最大变化量。 */
    public BigDecimal maxImpact() {
        return maxImpact;
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "rule value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("mood rule values must be positive");
        }
        return normalized;
    }
}
