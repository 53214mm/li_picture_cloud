package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrowthRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

    @Test
    void createsAppendOnlyFactAndAssignsOnePersistedId() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        FeedingGrowth growth = companion.feed(PictureNutrition.demo(42L,
                new TraitDelta(new BigDecimal("0.50"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 18L), "图片营养"),
                new FeedingContext(false, 0L, 0L), CompanionBalance.v1());

        GrowthRecord record = GrowthRecord.from(21L, 11L, 102L, growth,
                NutritionMode.DEMO_DETERMINISTIC, false,
                "growth-record-key01", "fef53056-2d9f-467d-9b1d-1afe9a6638fe", NOW);
        GrowthRecord persisted = record.withId(31L);

        assertThat(persisted.id()).isEqualTo(31L);
        assertThat(persisted.companionAfter()).isEqualTo(growth.companionAfter());
        assertThat(persisted.skillExperienceDelta()).containsEntry(CompanionSkill.IMAGE_OBSERVATION, 18L);
        assertThatThrownBy(() -> persisted.withId(32L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidAppendOnlyFacts() {
        Companion companion = Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
        assertThatThrownBy(() -> new GrowthRecord(null, 0L, 11L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(), Map.of(), companion,
                "reason", NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1",
                "growth-record-key01", "fef53056-2d9f-467d-9b1d-1afe9a6638fe", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrowthRecord(null, 21L, 11L, 102L,
                GrowthEventType.PICTURE_FED, 0L, TraitDelta.zero(),
                Map.of(CompanionSkill.STORY_CREATION, -1L), companion,
                "reason", NutritionMode.DEMO_DETERMINISTIC, false, "life-core-v1",
                "growth-record-key01", "fef53056-2d9f-467d-9b1d-1afe9a6638fe", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
