package com.li.lipicturecloud.domain.recipe;

import java.util.List;
import java.util.Objects;

/**
 * 配方定义的强类型形态：WHEN + 最多 5 个 IF + 一个 THEN。
 * 构造即校验（封闭条件集、分类安全纯文本、费用上限），是配方 JSON 的语义契约。
 */
public record RecipeDefinition(
        RecipeWhen when,
        List<RecipeIfCondition> conditions,
        RecipeThen then) {

    public static final int MAX_CONDITIONS = 5;

    public RecipeDefinition {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.size() > MAX_CONDITIONS) {
            throw new IllegalArgumentException("at most " + MAX_CONDITIONS + " IF conditions");
        }
        conditions = List.copyOf(conditions);
        Objects.requireNonNull(then, "then");
    }
}
