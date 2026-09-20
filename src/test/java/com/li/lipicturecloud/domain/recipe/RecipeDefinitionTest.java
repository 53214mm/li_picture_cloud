package com.li.lipicturecloud.domain.recipe;

import com.li.lipicturecloud.domain.airuntime.CreationKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeDefinitionTest {

    @Test
    void buildsValidDefinitionWithUpToFiveConditions() {
        RecipeDefinition definition = new RecipeDefinition(
                new RecipeWhen(RecipeWhenType.WEEKLY_REVIEW),
                List.of(new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceCategory("旅行"),
                        new RecipeIfCondition.MaxTrialCost(3L)),
                new RecipeThen(CreationKind.STORY_DRAFT));

        assertThat(definition.conditions()).hasSize(3);
        assertThat(definition.then().capability()).isEqualTo(CreationKind.STORY_DRAFT);
    }

    @Test
    void rejectsMoreThanFiveConditionsAndInvalidConditionValues() {
        assertThatThrownBy(() -> new RecipeDefinition(
                new RecipeWhen(RecipeWhenType.ANNIVERSARY),
                List.of(new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceSpacePrivate()),
                new RecipeThen(CreationKind.EMOJI_DRAFT)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeIfCondition.SourceCategory("带\u0007控制"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecipeIfCondition.SourceCategory("x".repeat(17)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecipeIfCondition.MaxTrialCost(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecipeIfCondition.MaxTrialCost(1_000_001L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullComponentsAndImmutableConditionList() {
        assertThatThrownBy(() -> new RecipeDefinition(null, List.of(),
                new RecipeThen(CreationKind.EMOJI_DRAFT)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RecipeDefinition(
                new RecipeWhen(RecipeWhenType.SIMILAR_STORY), null,
                new RecipeThen(CreationKind.EMOJI_DRAFT)))
                .isInstanceOf(NullPointerException.class);

        List<RecipeIfCondition> mutable = new java.util.ArrayList<>(List.of(
                new RecipeIfCondition.SourceSpacePrivate()));
        RecipeDefinition definition = new RecipeDefinition(
                new RecipeWhen(RecipeWhenType.SIMILAR_STORY), mutable,
                new RecipeThen(CreationKind.EMOJI_DRAFT));
        mutable.add(new RecipeIfCondition.SourceCategory("花园"));
        assertThat(definition.conditions()).hasSize(1);
    }

    @Test
    void thenRejectsNonWhitelistedCapabilities() {
        assertThatThrownBy(() -> new RecipeThen(null))
                .isInstanceOf(NullPointerException.class);
    }
}
