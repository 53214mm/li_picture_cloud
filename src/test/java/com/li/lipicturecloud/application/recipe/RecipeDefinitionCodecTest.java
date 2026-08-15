package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.recipe.RecipeDefinition;
import com.li.lipicturecloud.domain.recipe.RecipeIfCondition;
import com.li.lipicturecloud.domain.recipe.RecipeThen;
import com.li.lipicturecloud.domain.recipe.RecipeWhen;
import com.li.lipicturecloud.domain.recipe.RecipeWhenType;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeDefinitionCodecTest {

    private final RecipeDefinitionCodec codec =
            new RecipeDefinitionCodec(new ObjectMapper());

    private static RecipeDefinition definition() {
        return new RecipeDefinition(
                new RecipeWhen(RecipeWhenType.WEEKLY_REVIEW),
                List.of(new RecipeIfCondition.SourceSpacePrivate(),
                        new RecipeIfCondition.SourceCategory("旅行"),
                        new RecipeIfCondition.MaxTrialCost(3L)),
                new RecipeThen(CreationKind.STORY_DRAFT));
    }

    @Test
    void encodeDecodeRoundTrips() {
        RecipeDefinitionCodec.RecipeDefinitionJson json = codec.encode(definition());

        RecipeDefinition decoded = codec.decode(json.whenJson(), json.ifJson(), json.thenJson());

        assertThat(decoded.when().type()).isEqualTo(RecipeWhenType.WEEKLY_REVIEW);
        assertThat(decoded.conditions()).containsExactly(
                new RecipeIfCondition.SourceSpacePrivate(),
                new RecipeIfCondition.SourceCategory("旅行"),
                new RecipeIfCondition.MaxTrialCost(3L));
        assertThat(decoded.then().capability()).isEqualTo(CreationKind.STORY_DRAFT);
    }

    @Test
    void decodeNodeRejectsUnknownFieldsTypesAndInvalidValues() {
        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW", "evil": "x"},
                 "conditions": [], "then": {"capability": "STORY_DRAFT"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "NOT_A_TRIGGER"},
                 "conditions": [], "then": {"capability": "STORY_DRAFT"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [{"type": "EXECUTE_CODE"}],
                 "then": {"capability": "STORY_DRAFT"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [{"type": "SOURCE_CATEGORY", "category": "带\\u0007控制"}],
                 "then": {"capability": "STORY_DRAFT"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [{"type": "MAX_TRIAL_COST", "units": 0}],
                 "then": {"capability": "STORY_DRAFT"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [], "then": {"capability": "UNKNOWN_PLAY"}}
                """))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [], "then": {"capability": "STORY_DRAFT", "target": "x"}}
                """))).isInstanceOf(BusinessException.class);

        // 顶层未知字段同样大声失败。
        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("""
                {"when": {"type": "WEEKLY_REVIEW"}, "conditions": [],
                 "then": {"capability": "STORY_DRAFT"}, "evil": 1}
                """))).isInstanceOf(BusinessException.class);
    }

    @Test
    void decodeNodeRejectsMoreThanFiveConditionsAndMissingFields() {
        String sixConditions = """
                {"when": {"type": "WEEKLY_REVIEW"},
                 "conditions": [
                   {"type": "SOURCE_SPACE_PRIVATE"}, {"type": "SOURCE_SPACE_PRIVATE"},
                   {"type": "SOURCE_SPACE_PRIVATE"}, {"type": "SOURCE_SPACE_PRIVATE"},
                   {"type": "SOURCE_SPACE_PRIVATE"}, {"type": "SOURCE_SPACE_PRIVATE"}],
                 "then": {"capability": "STORY_DRAFT"}}
                """;
        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree(sixConditions)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 5 条");

        assertThatThrownBy(() -> codec.decodeNode(new ObjectMapper().readTree("{}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> codec.decodeNode(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decodeRejectsMalformedStoredJson() {
        assertThatThrownBy(() -> codec.decode("not json", "[]", "{}"))
                .isInstanceOf(BusinessException.class);
    }
}
