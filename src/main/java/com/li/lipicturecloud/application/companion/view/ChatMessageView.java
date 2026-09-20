package com.li.lipicturecloud.application.companion.view;

import java.time.Instant;

/**
 * 一条伙伴对话消息的展示视图。
 */
public record ChatMessageView(
        Long id,
        String role,
        String content,
        String modelProvider,
        String modelCode,
        Instant createdTime) {
}
