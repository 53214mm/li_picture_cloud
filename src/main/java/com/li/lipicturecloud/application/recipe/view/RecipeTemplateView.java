package com.li.lipicturecloud.application.recipe.view;

/**
 * 官方模板视图：不可编辑系统配方的可复制起点。
 *
 * <p>{@code available=false} 表示该模板依赖的能力尚未开放：前端必须把它渲染成明确禁用的
 * 选项并展示 {@code unavailableReason}，服务端也会拒绝用它创建配方。</p>
 */
public record RecipeTemplateView(
        String code,
        String name,
        String description,
        String whenJson,
        String ifJson,
        String thenJson,
        boolean available,
        String unavailableReason) {
}
