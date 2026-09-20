package com.li.lipicturecloud.application.recipe.view;

/**
 * 配方可组合能力的可用性视图：编辑器用它把未开放能力渲染成禁用选项，
 * 服务端在发布/试运行/执行时用同一处判断拒绝未开放能力。
 */
public record RecipeCapabilityView(
        String capability,
        boolean open,
        String unavailableReason) {
}
