package com.li.lipicturecloud.domain.recipe;

/**
 * 配方生命周期状态。DRAFT 仅可编辑/发布；ENABLED 参与触发；DISABLED 即时停用。
 */
public enum RecipeStatus {
    DRAFT,
    ENABLED,
    DISABLED
}
