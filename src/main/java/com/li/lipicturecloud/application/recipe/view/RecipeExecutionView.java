package com.li.lipicturecloud.application.recipe.view;

import java.time.Instant;
import java.util.List;

/**
 * 配方执行回放视图：只含安全字段（命中快照/报价/来源图片 ID/任务引用/安全错误码），
 * 不含图片字节、提示词正文、密钥或用户原文。
 *
 * <p>{@code sourcePictureIds} 是本次预览/触发绑定的图片集合：用户确认执行时用的就是它，
 * 改选图片必须重新试运行。</p>
 */
public record RecipeExecutionView(
        long id,
        long recipeId,
        int recipeVersion,
        String status,
        Instant triggeredTime,
        String matchedJson,
        String quoteJson,
        List<Long> sourcePictureIds,
        String opportunityKey,
        Long creationTaskId,
        String safeErrorCode,
        Instant createdTime) {
}
