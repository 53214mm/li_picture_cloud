package com.li.lipicturecloud.domain.recipe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeVersionTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void createsVersionWithJsonConstraints() {
        RecipeVersion version = RecipeVersion.create(9L, 1, "{\"type\":\"WEEKLY_REVIEW\"}",
                "[{\"type\":\"SOURCE_SPACE_PRIVATE\"}]", "{\"capability\":\"STORY_DRAFT\"}", NOW);

        assertThat(version.recipeId()).isEqualTo(9L);
        assertThat(version.version()).isEqualTo(1);
        assertThat(version.id()).isNull();
    }

    @Test
    void rejectsBlankOversizedAndUnsafeJson() {
        assertThatThrownBy(() -> RecipeVersion.create(9L, 1, " ", "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeVersion.create(9L, 1, "{}", "[]", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecipeVersion.create(9L, 1, "x".repeat(4001), "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeVersion.create(9L, 1, "带\u0007控制", "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeVersion.create(0L, 1, "{}", "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeVersion.create(9L, 0, "{}", "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeVersion.create(9L, 1, "{}", "[]", "{}", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        RecipeVersion created = RecipeVersion.create(9L, 2, "{}", "[]", "{}", NOW);
        RecipeVersion persisted = created.withId(4L);
        assertThat(persisted.id()).isEqualTo(4L);
        assertThatThrownBy(() -> persisted.withId(5L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoreRequiresPositivePersistedId() {
        assertThatThrownBy(() -> RecipeVersion.restore(null, 9L, 1, "{}", "[]", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
