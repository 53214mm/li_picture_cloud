package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record CompanionTraits(
        BigDecimal curiosity,
        BigDecimal enthusiasm,
        BigDecimal playfulness,
        BigDecimal empathy,
        BigDecimal creativity) {

    private static final BigDecimal MINIMUM = new BigDecimal("-100.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("100.00");

    public CompanionTraits {
        curiosity = normalizeAndValidate(curiosity);
        enthusiasm = normalizeAndValidate(enthusiasm);
        playfulness = normalizeAndValidate(playfulness);
        empathy = normalizeAndValidate(empathy);
        creativity = normalizeAndValidate(creativity);
    }

    public static CompanionTraits neutral() {
        return new CompanionTraits(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public List<BigDecimal> values() {
        return List.of(curiosity, enthusiasm, playfulness, empathy, creativity);
    }

    private static BigDecimal normalizeAndValidate(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "trait value")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(MINIMUM) < 0 || normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("trait must be between -100.00 and 100.00");
        }
        return normalized;
    }
}
