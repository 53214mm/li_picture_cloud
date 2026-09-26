package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 一次成长对五条性格轴提出或实际应用的增量值对象。
 *
 * <p>它只统一数值精度，不负责上限裁剪；最终可应用增量由 {@link CompanionBalance} 决定。</p>
 */
public record TraitDelta(
        BigDecimal curiosity,
        BigDecimal enthusiasm,
        BigDecimal playfulness,
        BigDecimal empathy,
        BigDecimal creativity) {

    public TraitDelta {
        curiosity = normalize(curiosity);
        enthusiasm = normalize(enthusiasm);
        playfulness = normalize(playfulness);
        empathy = normalize(empathy);
        creativity = normalize(creativity);
    }

    public static TraitDelta zero() {
        return new TraitDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public List<BigDecimal> values() {
        return List.of(curiosity, enthusiasm, playfulness, empathy, creativity);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return Objects.requireNonNull(value, "trait delta")
                .setScale(2, RoundingMode.HALF_UP);
    }
}
