package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CompanionBalancePropertyTest {

    @Test
    void extremePersistedExperienceIsResolvedWithoutOverflow() {
        CompanionBalance balance = CompanionBalance.v1();

        assertThatCode(() -> balance.levelFor(Long.MAX_VALUE)).doesNotThrowAnyException();
        int level = balance.levelFor(Long.MAX_VALUE);
        assertThat(level).isPositive();
        assertThat(balance.totalExperienceForLevel(level)).isLessThanOrEqualTo(Long.MAX_VALUE);
    }

    @Test
    void arbitraryFeedSequenceKeepsCoreInvariants() {
        CompanionBalance balance = CompanionBalance.v1();
        Companion companion = Companion.awaken(9L, balance).persistedAs(12L);
        Random random = new Random(20260811L);
        long previousExperience = 0L;

        for (int index = 0; index < 5_000; index++) {
            TraitDelta requested = new TraitDelta(
                    bd(random.nextInt(-500, 501), 2),
                    bd(random.nextInt(-500, 501), 2),
                    bd(random.nextInt(-500, 501), 2),
                    bd(random.nextInt(-500, 501), 2),
                    bd(random.nextInt(-500, 501), 2));
            PictureNutrition nutrition = PictureNutrition.demo(
                    random.nextLong(0, 500), requested,
                    Map.of(CompanionSkill.STORY_CREATION, random.nextLong(0, 100)),
                    "确定性属性测试");
            boolean repeat = random.nextBoolean();
            FeedingGrowth growth = companion.feed(nutrition,
                    new FeedingContext(repeat, random.nextLong(0, 350), random.nextLong(0, 5)),
                    balance);
            companion = growth.companionAfter();

            assertThat(companion.lifeExperience()).isGreaterThanOrEqualTo(previousExperience);
            assertThat(companion.traits().values()).allSatisfy(value ->
                    assertThat(value).isBetween(new BigDecimal("-100.00"), new BigDecimal("100.00")));
            assertThat(growth.traitDelta().values()).allSatisfy(value ->
                    assertThat(value.abs()).isLessThanOrEqualTo(new BigDecimal("1.00")));
            assertThat(companion.level()).isEqualTo(balance.levelFor(companion.lifeExperience()));
            assertThat(companion.lifeStage()).isEqualTo(balance.stageFor(companion.level()));
            previousExperience = companion.lifeExperience();
        }
    }

    private static BigDecimal bd(int unscaled, int scale) {
        return BigDecimal.valueOf(unscaled, scale);
    }
}
