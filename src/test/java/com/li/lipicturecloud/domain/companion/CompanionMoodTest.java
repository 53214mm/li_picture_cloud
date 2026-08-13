package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionMoodTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private final CompanionMoodRules rules = CompanionMoodRules.v1();

    @Test
    void neutralMoodStartsAtZero() {
        CompanionMood mood = CompanionMood.neutral(11L, NOW);

        assertThat(mood.energy()).isEqualByComparingTo("0.00");
        assertThat(mood.joy()).isEqualByComparingTo("0.00");
        assertThat(mood.revision()).isZero();
        assertThat(mood.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void decaysEveryFullHourTowardsZero() {
        CompanionMood mood = new CompanionMood(null, 11L,
                bd("60.00"), bd("40.00"), bd("20.00"), bd("10.00"), bd("5.00"),
                3L, NOW);

        CompanionMood decayed = mood.decayed(NOW.plusSeconds(2 * 3600 + 59), rules);

        // 每小时 -5.00，只按完整小时计算。
        assertThat(decayed.energy()).isEqualByComparingTo("50.00");
        assertThat(decayed.joy()).isEqualByComparingTo("30.00");
        assertThat(decayed.loneliness()).isEqualByComparingTo("10.00");
        assertThat(decayed.inspiration()).isEqualByComparingTo("0.00");
        assertThat(decayed.irritation()).isEqualByComparingTo("0.00");
        assertThat(decayed.revision()).isEqualTo(4L);
        assertThat(decayed.updatedAt()).isEqualTo(NOW.plusSeconds(2 * 3600 + 59));
    }

    @Test
    void decayNeverGoesNegative() {
        CompanionMood mood = CompanionMood.neutral(11L, NOW);
        CompanionMood decayed = mood.decayed(NOW.plusSeconds(100 * 3600L), rules);

        assertThat(decayed.energy()).isEqualByComparingTo("0.00");
        assertThat(decayed.sameValuesAs(mood)).isTrue();
    }

    @Test
    void returnsSelfWhenNothingDecays() {
        CompanionMood mood = CompanionMood.neutral(11L, NOW);
        assertThat(mood.decayed(NOW.plusSeconds(3600L), rules)).isSameAs(mood);
        assertThat(mood.decayed(NOW.plusSeconds(59), rules)).isSameAs(mood);
    }

    @Test
    void appliesImpactAndCapsSingleAxisMovement() {
        CompanionMood mood = CompanionMood.neutral(11L, NOW);

        CompanionMood after = mood.apply(new MoodImpact(
                bd("50.00"), bd("8.00"), bd("-6.00"), bd("2.00"), bd("1.00")), NOW, rules);

        assertThat(after.energy()).isEqualByComparingTo("15.00");
        assertThat(after.joy()).isEqualByComparingTo("8.00");
        assertThat(after.loneliness()).isEqualByComparingTo("0.00");
        assertThat(after.inspiration()).isEqualByComparingTo("2.00");
        assertThat(after.irritation()).isEqualByComparingTo("1.00");
        assertThat(after.revision()).isEqualTo(1L);
    }

    @Test
    void impactCannotPushBeyondBounds() {
        CompanionMood mood = new CompanionMood(null, 11L,
                bd("95.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                1L, NOW);

        CompanionMood after = mood.apply(new MoodImpact(
                bd("15.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00")), NOW, rules);

        assertThat(after.energy()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new CompanionMood(null, 11L,
                bd("101.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionMood.neutral(0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionMood(null, 11L,
                bd("0.00"), bd("-0.01"), bd("0.00"), bd("0.00"), bd("0.00"), 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
