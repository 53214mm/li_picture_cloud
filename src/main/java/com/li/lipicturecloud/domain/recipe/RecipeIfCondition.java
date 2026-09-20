package com.li.lipicturecloud.domain.recipe;

import java.util.Objects;

/**
 * 配方 IF 条件（0—5 条，全部满足才执行）。条件集合是封闭的：
 * 只能收紧来源空间、图片分类与费用上限，无法表达任何扩权语义。
 */
public sealed interface RecipeIfCondition
        permits RecipeIfCondition.SourceSpacePrivate,
                RecipeIfCondition.SourceCategory,
                RecipeIfCondition.MaxTrialCost {

    /** 只处理私有空间的图片（收紧来源）。 */
    record SourceSpacePrivate() implements RecipeIfCondition {
    }

    /** 只处理指定分类的图片；分类是安全纯文本且不携带图片名或用户原文。 */
    record SourceCategory(String category) implements RecipeIfCondition {

        public SourceCategory {
            String normalized = Objects.requireNonNull(category, "category").strip();
            if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 16) {
                throw new IllegalArgumentException("category must be 1-16 characters");
            }
            if (normalized.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("category must be safe plain text");
            }
            category = normalized;
        }
    }

    /** 本次执行的平台试用额度上限（收紧费用；BYOK 路径不受此影响）。 */
    record MaxTrialCost(long units) implements RecipeIfCondition {

        public static final long MAX_UNITS = 1_000_000L;

        public MaxTrialCost {
            if (units < 1 || units > MAX_UNITS) {
                throw new IllegalArgumentException("cost cap must be 1.." + MAX_UNITS);
            }
        }
    }
}
