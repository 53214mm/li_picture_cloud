package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipImpactTest {

    @Test
    void zeroImpactIsNeutralAndDetectable() {
        RelationshipImpact zero = RelationshipImpact.zero();

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.familiarity()).isEqualByComparingTo("0.00");
        assertThat(zero.recentFeedback()).isEqualByComparingTo("0.00");
    }

    @Test
    void nonzeroImpactReportsNotZero() {
        RelationshipImpact impact = new RelationshipImpact(
                bd("0.00"), bd("1.00"), bd("0.00"), bd("0.00"), bd("0.00"));

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
            assertThat(new RelationshipImpact(axis[0], axis[1], axis[2], axis[3], axis[4]).isZero()).isFalse();
        }
    }

    @Test
    void impactValuesAreNormalizedAndBounded() {
        RelationshipImpact impact = new RelationshipImpact(
                bd("5.005"), bd("-2.5"), bd("0"), bd("0"), bd("1.234"));

        assertThat(impact.familiarity()).isEqualByComparingTo("5.01");
        assertThat(impact.trust()).isEqualByComparingTo("-2.50");
        assertThat(impact.recentFeedback()).isEqualByComparingTo("1.23");
    }

    @Test
    void rejectsImpactBeyondHundred() {
        assertThatThrownBy(() -> new RelationshipImpact(
                bd("100.01"), bd("0"), bd("0"), bd("0"), bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RelationshipImpact(
                bd("0"), bd("0"), bd("0"), bd("0"), bd("-100.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RelationshipImpact(null, bd("0"), bd("0"), bd("0"), bd("0")))
                .isInstanceOf(NullPointerException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
