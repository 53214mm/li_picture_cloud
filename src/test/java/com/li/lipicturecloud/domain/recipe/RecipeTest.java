package com.li.lipicturecloud.domain.recipe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Test
    void createStartsDraftAtRevisionZero() {
        Recipe recipe = Recipe.create(7L, "旅行回顾", NOW);

        assertThat(recipe.subjectId()).isEqualTo(7L);
        assertThat(recipe.name()).isEqualTo("旅行回顾");
        assertThat(recipe.status()).isEqualTo(RecipeStatus.DRAFT);
        assertThat(recipe.revision()).isZero();
    }

    @Test
    void enableAndDisableAdvanceRevisionByExactlyOne() {
        Recipe recipe = Recipe.create(7L, "旅行回顾", NOW).withId(9L);

        Recipe enabled = recipe.enable(NOW);
        assertThat(enabled.status()).isEqualTo(RecipeStatus.ENABLED);
        assertThat(enabled.revision()).isEqualTo(1L);

        Recipe disabled = enabled.disable(NOW);
        assertThat(disabled.status()).isEqualTo(RecipeStatus.DISABLED);
        assertThat(disabled.revision()).isEqualTo(2L);

        // DISABLED 可以再次启用；DRAFT 不能直接禁用。
        assertThat(disabled.enable(NOW).status()).isEqualTo(RecipeStatus.ENABLED);
        assertThatThrownBy(() -> recipe.disable(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidIdentityNameAndTimestamps() {
        assertThatThrownBy(() -> Recipe.create(0L, "旅行回顾", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recipe.create(7L, "", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recipe.create(7L, "带\u0007控制字符", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recipe.create(7L, "x".repeat(65), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recipe(9L, 7L, "旅行回顾", RecipeStatus.DRAFT, 0L,
                NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recipe(9L, 7L, "旅行回顾", RecipeStatus.DRAFT, 0L,
                NOW, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Recipe(-1L, 7L, "旅行回顾", RecipeStatus.DRAFT, 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        Recipe created = Recipe.create(7L, "旅行回顾", NOW);
        Recipe persisted = created.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
