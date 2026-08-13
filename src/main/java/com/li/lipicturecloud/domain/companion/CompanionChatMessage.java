package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Objects;

/**
 * 一条追加式的伙伴对话消息。
 *
 * <p>消息只属于一个主体的伙伴；内容允许包含普通链接文本（聊天是用户自己的内容，
 * 展示层由 Vue 转义），但拒绝 ISO 控制字符。日志与指标不得记录内容原文。</p>
 */
public record CompanionChatMessage(
        Long id,
        long companionId,
        long subjectId,
        CompanionChatRole role,
        String content,
        String modelProvider,
        String modelCode,
        Instant createdTime) {

    public static final int MAX_CONTENT_CODE_POINTS = 1000;

    public CompanionChatMessage {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("invalid chat message identity");
        }
        Objects.requireNonNull(role, "role");
        content = checkContent(content);
        if (modelProvider != null && modelProvider.length() > 64) {
            throw new IllegalArgumentException("modelProvider is too long");
        }
        if (modelCode != null && modelCode.length() > 64) {
            throw new IllegalArgumentException("modelCode is too long");
        }
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static CompanionChatMessage user(long companionId, long subjectId, String content, Instant now) {
        return new CompanionChatMessage(null, companionId, subjectId, CompanionChatRole.USER,
                content, null, null, now);
    }

    public static CompanionChatMessage companion(long companionId, long subjectId, String content,
                                                 String modelProvider, String modelCode, Instant now) {
        return new CompanionChatMessage(null, companionId, subjectId, CompanionChatRole.COMPANION,
                content, modelProvider, modelCode, now);
    }

    public CompanionChatMessage withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionChatMessage(persistedId, companionId, subjectId, role,
                content, modelProvider, modelCode, createdTime);
    }

    private static String checkContent(String value) {
        String normalized = Objects.requireNonNull(value, "content").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_CONTENT_CODE_POINTS) {
            throw new IllegalArgumentException("chat content must be 1-1000 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("chat content must not contain control characters");
        }
        return normalized;
    }
}
