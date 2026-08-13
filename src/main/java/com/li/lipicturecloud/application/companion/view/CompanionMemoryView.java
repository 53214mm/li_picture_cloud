package com.li.lipicturecloud.application.companion.view;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 记忆的展示视图。
 *
 * <p>失效或已删除的记忆不携带内容原文，只返回状态与原因；来源图片仍以 ID 引用。</p>
 */
public record CompanionMemoryView(
        Long id,
        String sourceType,
        String content,
        String originalContent,
        BigDecimal confidence,
        String status,
        String invalidatedReason,
        Long sourcePictureId,
        Instant createdTime,
        Instant updatedTime) {
}
