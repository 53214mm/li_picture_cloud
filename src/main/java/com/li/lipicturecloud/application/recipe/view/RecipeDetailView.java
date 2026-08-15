package com.li.lipicturecloud.application.recipe.view;

import java.util.List;

/**
 * 配方详情视图：配方 + 全部版本 + 最新版本的定义。
 */
public record RecipeDetailView(
        RecipeView recipe,
        List<RecipeVersionView> versions,
        RecipeVersionView latest) {
}
