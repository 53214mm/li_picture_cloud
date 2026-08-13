package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValidationTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String KEY = "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0";
    private static final String FINGERPRINT = "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d";
    private static final String CORRELATION = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

    @Test
    void validatesEveryCompanionAggregateBoundary() {
        Map<CompanionSkill, Long> skills = allSkills(0L);
        assertThatThrownBy(() -> new Companion(0L, 7L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), skills, "life-core-v1", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Companion(11L, 7L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), skills, "other", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Companion(11L, 7L, 100L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), skills, "life-core-v1", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Companion(11L, 7L, 0L, 1, CompanionStage.SEEDLING,
                CompanionTraits.neutral(), skills, "life-core-v1", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        Map<CompanionSkill, Long> negativeSkills = allSkills(0L);
        negativeSkills.put(CompanionSkill.IMAGE_FUSION, -1L);
        assertThatThrownBy(() -> new Companion(11L, 7L, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), negativeSkills, "life-core-v1", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        assertThatThrownBy(() -> companion.feed(PictureNutrition.demo(1L, TraitDelta.zero(), Map.of(), "feed"),
                new FeedingContext(false, 0L, 0L), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesTraitAndContextOppositeBoundaries() {
        assertThatThrownBy(() -> new CompanionTraits(new BigDecimal("-100.01"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingContext(false, 0L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAndNegativeSkillMapsBeforeFreezingThem() {
        Map<CompanionSkill, Long> nullKey = new HashMap<>();
        nullKey.put(null, 1L);
        Map<CompanionSkill, Long> nullValue = new HashMap<>();
        nullValue.put(CompanionSkill.EMOJI_CREATION, null);
        Map<CompanionSkill, Long> negative = new HashMap<>();
        negative.put(CompanionSkill.EMOJI_CREATION, -1L);

        assertThatThrownBy(() -> PictureNutrition.demo(1L, TraitDelta.zero(), nullKey, "picture"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(1L, TraitDelta.zero(), nullValue, "picture"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingGrowth(companion(), GrowthEventType.PICTURE_FED, 0L,
                TraitDelta.zero(), negative, "growth", "life-core-v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrowthRecord(null, 21L, 11L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(), nullValue, companion(), "record",
                NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1", KEY, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesFeedingRunConstructorAndTransitionsAtEveryBoundary() {
        FeedingRun run = run();
        assertThatThrownBy(() -> new FeedingRun(0L, 11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 0L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                null, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                FeedingRunStatus.PROCESSING, null, false,
                null, null, null, null, 1, 0L, NOW, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                0L, null, null, null, 1, 0L, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.persistedAs(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.completed(31L, NOW.plusSeconds(1)).restarted(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.failed("CODE", "message", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesGrowthRecordIdentityAndEveryPersistedIdCase() {
        GrowthRecord unpersisted = new GrowthRecord(null, 21L, 11L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(), Map.of(), companion(), "record",
                NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1", KEY, CORRELATION, NOW);
        assertThatThrownBy(() -> unpersisted.withId(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GrowthRecord(0L, 21L, 11L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(), Map.of(), companion(), "record",
                NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1", KEY, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrowthRecord(null, 21L, 0L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(), Map.of(), companion(), "record",
                NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1", KEY, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrowthRecord(null, 21L, 11L, 102L,
                GrowthEventType.PICTURE_FED, -1L, TraitDelta.zero(), Map.of(), companion(), "record",
                NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1", KEY, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Companion companion() {
        return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
    }

    private static FeedingRun run() {
        return FeedingRun.processing(11L, 7L, 102L, KEY, FINGERPRINT, CORRELATION,
                NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
    }

    private static Map<CompanionSkill, Long> allSkills(long experience) {
        Map<CompanionSkill, Long> skills = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            skills.put(skill, experience);
        }
        return skills;
    }
}
