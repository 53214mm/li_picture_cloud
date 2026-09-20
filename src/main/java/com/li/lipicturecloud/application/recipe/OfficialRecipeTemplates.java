package com.li.lipicturecloud.application.recipe;

import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.recipe.RecipeDefinition;
import com.li.lipicturecloud.domain.recipe.RecipeIfCondition;
import com.li.lipicturecloud.domain.recipe.RecipeThen;
import com.li.lipicturecloud.domain.recipe.RecipeWhen;
import com.li.lipicturecloud.domain.recipe.RecipeWhenType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 官方模板：不可编辑的系统配方定义，是可复制的起点。
 * 模板只组合白名单能力与收紧条件，不携带任何扩权语义。
 */
public final class OfficialRecipeTemplates {

    public static final String TRAVEL_REVIEW = "travel_review";
    public static final String BIRTHDAY_STORY = "birthday_story";
    public static final String WEEKLY_EMOJI = "weekly_emoji";
    public static final String OLD_PHOTO_REMASTER = "old_photo_remaster";

    private static final Map<String, RecipeDefinition> TEMPLATES = Map.of(
            TRAVEL_REVIEW, new RecipeDefinition(
                    new RecipeWhen(RecipeWhenType.SIMILAR_STORY),
                    List.of(new RecipeIfCondition.SourceCategory("旅行")),
                    new RecipeThen(CreationKind.STORY_DRAFT)),
            BIRTHDAY_STORY, new RecipeDefinition(
                    new RecipeWhen(RecipeWhenType.ANNIVERSARY),
                    List.of(),
                    new RecipeThen(CreationKind.STORY_DRAFT)),
            WEEKLY_EMOJI, new RecipeDefinition(
                    new RecipeWhen(RecipeWhenType.WEEKLY_REVIEW),
                    List.of(),
                    new RecipeThen(CreationKind.EMOJI_DRAFT)),
            OLD_PHOTO_REMASTER, new RecipeDefinition(
                    new RecipeWhen(RecipeWhenType.SIMILAR_STORY),
                    List.of(new RecipeIfCondition.SourceSpacePrivate()),
                    new RecipeThen(CreationKind.IMAGE_FUSION)));

    private static final Map<String, String> NAMES = Map.of(
            TRAVEL_REVIEW, "旅行回顾",
            BIRTHDAY_STORY, "生日故事",
            WEEKLY_EMOJI, "每周表情",
            OLD_PHOTO_REMASTER, "旧照重制");

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            TRAVEL_REVIEW, "空间里出现新的旅行图片时，为它们写一段故事草稿。",
            BIRTHDAY_STORY, "纪念日到来时，用伙伴的语气讲一个生日故事。",
            WEEKLY_EMOJI, "每周回顾时，从近期图片里挑一句俏皮话候选。",
            OLD_PHOTO_REMASTER, "私有空间出现相似旧照时，把它们融合成一张新作品。");

    private OfficialRecipeTemplates() {
    }

    public static Optional<RecipeDefinition> definition(String templateCode) {
        return Optional.ofNullable(TEMPLATES.get(templateCode));
    }

    public static Optional<String> name(String templateCode) {
        return Optional.ofNullable(NAMES.get(templateCode));
    }

    public static Optional<String> description(String templateCode) {
        return Optional.ofNullable(DESCRIPTIONS.get(templateCode));
    }

    public static List<String> allCodes() {
        return List.of(TRAVEL_REVIEW, BIRTHDAY_STORY, WEEKLY_EMOJI, OLD_PHOTO_REMASTER);
    }
}
