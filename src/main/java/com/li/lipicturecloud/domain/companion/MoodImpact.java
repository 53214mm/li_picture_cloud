package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 一次喂养请求的情绪变化候选：可正可负，最终幅度由 {@link CompanionMoodRules} 截断。
 */
public record MoodImpact(
        BigDecimal energy,
        BigDecimal joy,
        BigDecimal loneliness,
        BigDecimal inspiration,
        BigDecimal irritation) {

    private static final BigDecimal LIMIT = new BigDecimal("100.00");

    public MoodImpact {
        energy = normalize(energy);
        joy = normalize(joy);
        loneliness = normalize(loneliness);
        inspiration = normalize(inspiration);
        irritation = normalize(irritation);
    }

    public static MoodImpact zero() {
        return new MoodImpact(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean isZero() {
        return energy.signum() == 0 && joy.signum() == 0 && loneliness.signum() == 0
                && inspiration.signum() == 0 && irritation.signum() == 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "impact value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(LIMIT) > 0) {
            throw new IllegalArgumentException("mood impact must be between -100.00 and 100.00");
        }
        return normalized;
    }
}
