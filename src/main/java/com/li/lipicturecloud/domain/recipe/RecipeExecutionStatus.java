package com.li.lipicturecloud.domain.recipe;

/**
 * 配方执行状态。创建即 DRY_RUN 或 EXECUTED；失败/拒绝为终态。
 */
public enum RecipeExecutionStatus {
    DRY_RUN,
    EXECUTED,
    FAILED,
    REJECTED
}
