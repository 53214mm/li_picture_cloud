package com.li.lipicturecloud.application.recipe.view;

/**
 * 官方模板视图：不可编辑系统配方的可复制起点。
 */
public record RecipeTemplateView(
        String code,
        String name,
        String description,
        String whenJson,
        String ifJson,
        String thenJson) {
}
