package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Objects;

/**
 * 创作候选（表情草稿等候选式玩法）：生成阶段追加，用户选择后转成作品草稿。
 * 文本必须是安全纯文本，绝不携带图片原文或链接。
 */
public record CreationCandidate(
        Long id,
        long taskId,
        int seq,
        String text,
        Instant createdTime) {

    public static final int MAX_CANDIDATES = 8;
    public static final int MAX_TEXT_CODE_POINTS = 200;

    public CreationCandidate {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (taskId <= 0 || seq < 0 || seq >= MAX_CANDIDATES) {
            throw new IllegalArgumentException("invalid candidate identity or sequence");
        }
        Objects.requireNonNull(text, "text");
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("candidate text must not be blank");
        }
        int length = stripped.codePointCount(0, stripped.length());
        if (length > MAX_TEXT_CODE_POINTS) {
            throw new IllegalArgumentException("candidate text exceeds "
                    + MAX_TEXT_CODE_POINTS + " characters");
        }
        if (stripped.codePoints().anyMatch(Character::isISOControl)
                || stripped.codePoints().anyMatch(CreationCandidate::isUnsafeFormatChar)
                || stripped.toLowerCase(java.util.Locale.ROOT).contains("http://")
                || stripped.toLowerCase(java.util.Locale.ROOT).contains("https://")) {
            throw new IllegalArgumentException("candidate text must be safe plain text");
        }
        text = stripped;
        Objects.requireNonNull(createdTime, "createdTime");
    }

    /** 拒绝双向控制符、零宽字符与行分隔符，防止渲染层被视觉欺骗。 */
    private static boolean isUnsafeFormatChar(int codePoint) {
        return (codePoint >= 0x200B && codePoint <= 0x200F)      // 零宽/格式控制
                || (codePoint >= 0x202A && codePoint <= 0x202E)  // 双向嵌入控制
                || (codePoint >= 0x2066 && codePoint <= 0x2069)  // 双向隔离控制
                || codePoint == 0xFEFF                          // BOM/零宽不换行空格
                || codePoint == 0x2028 || codePoint == 0x2029;   // 行/段分隔符
    }

    public CreationCandidate withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CreationCandidate(persistedId, taskId, seq, text, createdTime);
    }
}
