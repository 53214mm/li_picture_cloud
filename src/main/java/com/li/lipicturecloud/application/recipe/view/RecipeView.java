package com.li.lipicturecloud.application.recipe.view;

import java.time.Instant;

/**
 * 配方列表视图：只含安全字段，不回显定义正文之外的任何敏感信息。
 */
public record RecipeView(
        long id,
        long subjectId,
        String name,
        String status,
        long revision,
        Integer latestVersion,
        Instant createdTime,
        Instant updatedTime) {
}
