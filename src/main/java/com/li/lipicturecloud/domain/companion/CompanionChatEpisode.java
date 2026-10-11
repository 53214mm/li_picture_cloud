package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Objects;

/**
 * 一段已经结束的聊天整理出的"经历"：她以后可能会想起的事。
 *
 * <p>summary 为空表示这段只是打招呼或闲扯，没有值得记住的内容；这一行仍然保留，
 * 用来推进整理进度。覆盖的消息范围是 [firstMessageId, lastMessageId]。</p>
 */
public record CompanionChatEpisode(
        Long id,
        long companionId,
        long subjectId,
        long firstMessageId,
        long lastMessageId,
        Instant startedTime,
        Instant endedTime,
        String summary,
        String modelProvider,
        String modelCode,
        Instant createdTime) {

    public static final int MAX_SUMMARY_CODE_POINTS = 160;

    public CompanionChatEpisode {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || firstMessageId <= 0 || lastMessageId <= 0) {
            throw new IllegalArgumentException("invalid episode identity");
        }
        Objects.requireNonNull(startedTime, "startedTime");
        Objects.requireNonNull(endedTime, "endedTime");
        if (endedTime.isBefore(startedTime)) {
            throw new IllegalArgumentException("episode ends before it starts");
        }
        summary = Objects.requireNonNull(summary, "summary").strip();
        if (summary.codePointCount(0, summary.length()) > MAX_SUMMARY_CODE_POINTS
                || summary.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid episode summary");
        }
        if (modelProvider != null && modelProvider.length() > 64 || modelCode != null && modelCode.length() > 64) {
            throw new IllegalArgumentException("model identity is too long");
        }
        Objects.requireNonNull(createdTime, "createdTime");
    }

    /** 是否有值得记住的内容。 */
    public boolean memorable() {
        return !summary.isEmpty();
    }
}
