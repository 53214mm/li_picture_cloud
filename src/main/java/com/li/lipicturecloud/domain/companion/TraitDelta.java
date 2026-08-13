package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

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
