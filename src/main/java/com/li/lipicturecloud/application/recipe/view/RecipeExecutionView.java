package com.li.lipicturecloud.application.recipe.view;

import java.time.Instant;

/**
 * 配方执行回放视图：只含安全字段（命中快照/报价/任务引用/安全错误码），
 * 不含图片字节、提示词正文、密钥或用户原文。
 */
public record RecipeExecutionView(
        long id,
        long recipeId,
        int recipeVersion,
        String status,
        Instant triggeredTime,
        String matchedJson,
        String quoteJson,
        Long creationTaskId,
        String safeErrorCode,
        Instant createdTime) {
}
