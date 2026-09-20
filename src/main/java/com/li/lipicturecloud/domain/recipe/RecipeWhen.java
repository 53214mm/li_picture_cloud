package com.li.lipicturecloud.domain.recipe;

import java.util.Objects;

/**
 * 配方 WHEN：复用 Q2 机会源类型，无扩展参数。
 */
public record RecipeWhen(RecipeWhenType type) {

    public RecipeWhen {
        Objects.requireNonNull(type, "type");
    }
}
