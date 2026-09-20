package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoodImpactTest {

    @Test
    void zeroImpactIsNeutralAndDetectable() {
        MoodImpact zero = MoodImpact.zero();

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.energy()).isEqualByComparingTo("0.00");
        assertThat(zero.irritation()).isEqualByComparingTo("0.00");
    }

    @Test
    void nonzeroImpactReportsNotZero() {
        MoodImpact impact = new MoodImpact(
                bd("1.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"));

        assertThat(impact.isZero()).isFalse();
    }

    @Test
    void anySingleNonZeroAxisBreaksIsZero() {
        BigDecimal[][] axes = {
                {bd("1"), bd("0"), bd("0"), bd("0"), bd("0")},
                {bd("0"), bd("1"), bd("0"), bd("0"), bd("0")},
                {bd("0"), bd("0"), bd("1"), bd("0"), bd("0")},
                {bd("0"), bd("0"), bd("0"), bd("1"), bd("0")},
                {bd("0"), bd("0"), bd("0"), bd("0"), bd("1")}
        };
        for (BigDecimal[] axis : axes) {
            assertThat(new MoodImpact(axis[0], axis[1], axis[2], axis[3], axis[4]).isZero()).isFalse();
        }
    }

    @Test
    void impactValuesAreNormalizedAndBounded() {
        MoodImpact impact = new MoodImpact(
                bd("3.145"), bd("-2.5"), bd("0"), bd("0"), bd("0"));

        assertThat(impact.energy()).isEqualByComparingTo("3.15");
        assertThat(impact.joy()).isEqualByComparingTo("-2.50");
        assertThat(impact.loneliness()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsImpactBeyondHundred() {
        assertThatThrownBy(() -> new MoodImpact(
                bd("100.01"), bd("0"), bd("0"), bd("0"), bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MoodImpact(
                bd("0"), bd("-100.01"), bd("0"), bd("0"), bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MoodImpact(null, bd("0"), bd("0"), bd("0"), bd("0")))
                .isInstanceOf(NullPointerException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
