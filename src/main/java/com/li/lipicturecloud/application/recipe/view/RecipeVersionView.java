package com.li.lipicturecloud.application.recipe.view;

import java.time.Instant;

/**
 * 配方版本视图：三段定义以 JSON 字符串回显（白名单键值，前端自行解析）。
 */
public record RecipeVersionView(
        long id,
        int version,
        String whenJson,
        String ifJson,
        String thenJson,
        Instant createdTime) {
}
