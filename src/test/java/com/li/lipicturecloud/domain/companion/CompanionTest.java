package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionTest {

    private final CompanionBalance balance = CompanionBalance.v1();

    @Test
    void awakensAsAZeroExperienceLight() {
        Companion companion = Companion.awaken(7L, balance);

        assertThat(companion.ownerId()).isEqualTo(7L);
        assertThat(companion.lifeExperience()).isZero();
        assertThat(companion.level()).isEqualTo(1);
        assertThat(companion.lifeStage()).isEqualTo(CompanionStage.LIGHT);
        assertThat(companion.traits()).isEqualTo(CompanionTraits.neutral());
        assertThat(companion.revision()).isZero();
    }

    @Test
    void appliesFullNutritionThroughBalanceCaps() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
        PictureNutrition nutrition = PictureNutrition.demo(
                200L,
                new TraitDelta(bd("5"), bd("0.4"), bd("0"), bd("0.2"), bd("0.3")),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 80L),
                "演示营养让伙伴练习了观察与叙事。");

        FeedingGrowth result = companion.feed(
                nutrition, new FeedingContext(false, 0L, 0L), balance);

        assertThat(result.eventType()).isEqualTo(GrowthEventType.PICTURE_FED);
        assertThat(result.lifeExperienceDelta()).isEqualTo(60L);
        assertThat(result.traitDelta().curiosity()).isEqualByComparingTo("1.00");
        assertThat(result.skillExperienceDelta())
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 25L);
        assertThat(result.companionAfter().revision()).isEqualTo(1L);
    }

    @Test
    void revisitingNeverRepeatsTraitOrSkillGrowth() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);

        FeedingGrowth result = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(true, 20L, 1L), balance);

        assertThat(result.eventType()).isEqualTo(GrowthEventType.PICTURE_REVISITED);
        assertThat(result.lifeExperienceDelta()).isEqualTo(1L);
        assertThat(result.traitDelta()).isEqualTo(TraitDelta.zero());
        assertThat(result.skillExperienceDelta()).isEmpty();
    }

    @Test
    void dailyAndLifetimeRepeatCapsCanReduceGrowthToZero() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);

        FeedingGrowth dailyCapped = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(false, 300L, 0L), balance);
        FeedingGrowth repeatCapped = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(true, 0L, 3L), balance);

        assertThat(dailyCapped.lifeExperienceDelta()).isZero();
        assertThat(repeatCapped.lifeExperienceDelta()).isZero();
    }

    @Test
    void levelThresholdsAndExperienceCapsAreStable() {
        assertThat(balance.levelFor(99L)).isEqualTo(1);
        assertThat(balance.levelFor(100L)).isEqualTo(2);
        assertThat(balance.levelFor(299L)).isEqualTo(2);
        assertThat(balance.levelFor(300L)).isEqualTo(3);
        assertThat(balance.fullFeedExperience(999L, 0L)).isEqualTo(60L);
        assertThat(balance.fullFeedExperience(60L, 299L)).isEqualTo(1L);
        assertThat(balance.fullFeedExperience(60L, 300L)).isZero();
    }

    @Test
    void restoredTraitsBeyondSoftLimitsOnlyMoveInwardByOne() {
        Companion high = restoredWithCuriosity(new BigDecimal("100.00"));
        Companion low = restoredWithCuriosity(new BigDecimal("-100.00"));

        FeedingGrowth highInward = high.feed(nutritionWithCuriosity("-5"), fullContext(), balance);
        FeedingGrowth highOutward = high.feed(nutritionWithCuriosity("5"), fullContext(), balance);
        FeedingGrowth lowInward = low.feed(nutritionWithCuriosity("5"), fullContext(), balance);
        FeedingGrowth lowOutward = low.feed(nutritionWithCuriosity("-5"), fullContext(), balance);

        assertThat(highInward.traitDelta().curiosity()).isEqualByComparingTo("-1.00");
        assertThat(highInward.companionAfter().traits().curiosity()).isEqualByComparingTo("99.00");
        assertThat(highOutward.traitDelta().curiosity()).isZero();
        assertThat(lowInward.traitDelta().curiosity()).isEqualByComparingTo("1.00");
        assertThat(lowInward.companionAfter().traits().curiosity()).isEqualByComparingTo("-99.00");
        assertThat(lowOutward.traitDelta().curiosity()).isZero();
    }

    @Test
    void traitsAtSoftLimitsNeverMoveOutwardAndNeverJumpMoreThanOne() {
        Companion atHigh = restoredWithCuriosity(new BigDecimal("80.00"));
        Companion atLow = restoredWithCuriosity(new BigDecimal("-80.00"));

        FeedingGrowth high = atHigh.feed(nutritionWithCuriosity("5"), fullContext(), balance);
        FeedingGrowth low = atLow.feed(nutritionWithCuriosity("-5"), fullContext(), balance);

        assertThat(high.traitDelta().curiosity()).isZero();
        assertThat(low.traitDelta().curiosity()).isZero();
        assertThat(high.companionAfter().traits().curiosity()).isEqualByComparingTo("80.00");
        assertThat(low.companionAfter().traits().curiosity()).isEqualByComparingTo("-80.00");
    }

    @Test
    void validatesAggregateRestorationAndImmutableSkillShape() {
        assertThatThrownBy(() -> Companion.restore(null, 7L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), zeroSkills(), balance.version(), 0L, balance))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Companion.restore(11L, 7L, 0L, 2, CompanionStage.LIGHT,
                CompanionTraits.neutral(), zeroSkills(), balance.version(), 0L, balance))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Companion(11L, 0L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), zeroSkills(), balance.version(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Companion(11L, 7L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), Map.of(), balance.version(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Companion.awaken(7L, balance).persistedAs(11L).persistedAs(12L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesBalanceBoundariesAndSkillRequests() {
        assertThatThrownBy(() -> balance.totalExperienceForLevel(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.levelFor(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.revisitExperience(-1L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.revisitExperience(0L, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.fullFeedExperience(1L, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.revisitExperience(0L, 0L)).isEqualTo(1L);
        assertThat(balance.revisitExperience(299L, 0L)).isEqualTo(1L);
        assertThat(balance.revisitExperience(300L, 0L)).isZero();

        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
        FeedingGrowth noSkill = companion.feed(PictureNutrition.demo(1L, TraitDelta.zero(),
                Map.of(CompanionSkill.STORY_CREATION, 0L), "无技能经验"), fullContext(), balance);
        assertThat(noSkill.skillExperienceDelta()).isEmpty();
    }

    @Test
    void validatesNutritionAndTraitInputs() {
        assertThatThrownBy(() -> new TraitDelta(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompanionTraits(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(1L, TraitDelta.zero(),
                Map.of(CompanionSkill.IMAGE_FUSION, -1L), "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(1L, TraitDelta.zero(), null, "invalid"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(1L, TraitDelta.zero(), Map.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PictureNutrition(1L, TraitDelta.zero(), Map.of(), "invalid", null, false))
                .isInstanceOf(NullPointerException.class);
    }

    private Companion restoredWithCuriosity(BigDecimal curiosity) {
        return Companion.restore(11L, 7L, 0L, 1, CompanionStage.LIGHT,
                new CompanionTraits(curiosity, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO), zeroSkills(),
                balance.version(), 0L, balance);
    }

    private FeedingContext fullContext() {
        return new FeedingContext(false, 0L, 0L);
    }

    private PictureNutrition nutritionWithCuriosity(String curiosity) {
        return PictureNutrition.demo(0L,
                new TraitDelta(bd(curiosity), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO), Map.of(), "性格练习");
    }

    private static Map<CompanionSkill, Long> zeroSkills() {
        Map<CompanionSkill, Long> skills = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            skills.put(skill, 0L);
        }
        return skills;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
