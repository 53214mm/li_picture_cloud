package com.li.lipicturecloud.domain.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 一条带来源、置信度和状态的伙伴记忆。
 *
 * <p>记忆从一次真实内容理解的成长记录派生，保留最初候选文案（{@code originalContent}）
 * 与当前文案（{@code content}）。用户可以确认、纠正、忽略或删除；来源图片撤权或消失后
 * 记忆失效为 {@link MemoryStatus#INVALIDATED}，不再对外暴露内容。</p>
 */
public record CompanionMemory(
        Long id,
        long companionId,
        long subjectId,
        Long pictureId,
        long growthRecordId,
        MemorySourceType sourceType,
        String content,
        String originalContent,
        BigDecimal confidence,
        MemoryStatus status,
        String invalidatedReason,
        long revision,
        Instant createdTime,
        Instant updatedTime) {

    public static final int MAX_CONTENT_CODE_POINTS = 300;

    public CompanionMemory {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || growthRecordId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid memory identity or revision");
        }
        if (pictureId != null && pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive or null");
        }
        Objects.requireNonNull(sourceType, "sourceType");
        content = checkContent(content);
        originalContent = checkContent(originalContent);
        Objects.requireNonNull(confidence, "confidence");
        confidence = confidence.setScale(3, RoundingMode.HALF_UP);
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        Objects.requireNonNull(status, "status");
        if (status == MemoryStatus.INVALIDATED) {
            invalidatedReason = checkReason(invalidatedReason);
        } else {
            if (invalidatedReason != null) {
                throw new IllegalArgumentException("only INVALIDATED memory may carry invalidatedReason");
            }
        }
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (updatedTime.isBefore(createdTime)) {
            throw new IllegalArgumentException("updatedTime cannot be before createdTime");
        }
    }

    /** 由一次完整成长记录派生出的待确认候选。 */
    public static CompanionMemory candidate(long companionId, long subjectId, Long pictureId,
                                            long growthRecordId, MemorySourceType sourceType,
                                            String content, BigDecimal confidence, Instant now) {
        Objects.requireNonNull(now, "now");
        return new CompanionMemory(null, companionId, subjectId, pictureId, growthRecordId,
                sourceType, content, content, confidence, MemoryStatus.PENDING, null, 0L, now, now);
    }

    public static CompanionMemory restore(Long id, long companionId, long subjectId, Long pictureId,
                                          long growthRecordId, MemorySourceType sourceType,
                                          String content, String originalContent, BigDecimal confidence,
                                          MemoryStatus status, String invalidatedReason,
                                          long revision, Instant createdTime, Instant updatedTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CompanionMemory(id, companionId, subjectId, pictureId, growthRecordId,
                sourceType, content, originalContent, confidence, status, invalidatedReason,
                revision, createdTime, updatedTime);
    }

    public CompanionMemory withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CompanionMemory(persistedId, companionId, subjectId, pictureId, growthRecordId,
                sourceType, content, originalContent, confidence, status, invalidatedReason,
                revision, createdTime, updatedTime);
    }

    public CompanionMemory confirm(Instant now) {
        requireMutable(status, "confirm");
        if (status == MemoryStatus.CONFIRMED) {
            return this;
        }
        return transition(MemoryStatus.CONFIRMED, content, null, now);
    }

    public CompanionMemory correct(String correctedContent, Instant now) {
        requireMutable(status, "correct");
        return transition(MemoryStatus.CONFIRMED, checkContent(correctedContent), null, now);
    }

    public CompanionMemory dismiss(Instant now) {
        requireMutable(status, "dismiss");
        if (status == MemoryStatus.DISMISSED) {
            return this;
        }
        return transition(MemoryStatus.DISMISSED, content, null, now);
    }

    public CompanionMemory invalidate(String reason, Instant now) {
        requireMutable(status, "invalidate");
        return transition(MemoryStatus.INVALIDATED, content, checkReason(reason), now);
    }

    public CompanionMemory delete(Instant now) {
        if (status == MemoryStatus.DELETED) {
            return this;
        }
        return transition(MemoryStatus.DELETED, content, null, now);
    }

    /** 是否仍处于可被用户操作或失效传播影响的状态。 */
    public boolean active() {
        return status == MemoryStatus.PENDING || status == MemoryStatus.CONFIRMED
                || status == MemoryStatus.DISMISSED;
    }

    /** 是否可以向当前主体展示记忆内容原文。 */
    public boolean exposesContent() {
        return status == MemoryStatus.PENDING || status == MemoryStatus.CONFIRMED
                || status == MemoryStatus.DISMISSED;
    }

    private CompanionMemory transition(MemoryStatus next, String nextContent, String reason, Instant now) {
        return new CompanionMemory(id, companionId, subjectId, pictureId, growthRecordId,
                sourceType, nextContent, originalContent, confidence, next, reason,
                Math.addExact(revision, 1L), createdTime, Objects.requireNonNull(now, "now"));
    }

    private static void requireMutable(MemoryStatus status, String action) {
        if (status == MemoryStatus.INVALIDATED || status == MemoryStatus.DELETED) {
            throw new IllegalStateException("cannot " + action + " a terminal memory");
        }
    }

    private static String checkReason(String reason) {
        String normalized = Objects.requireNonNull(reason, "invalidatedReason").strip();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("invalidatedReason must be 1-64 characters");
        }
        return normalized;
    }

    private static String checkContent(String value) {
        String normalized = Objects.requireNonNull(value, "memory content").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_CONTENT_CODE_POINTS) {
            throw new IllegalArgumentException("memory content must be 1-300 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)
                || containsExternalLink(normalized)) {
            throw new IllegalArgumentException("memory content must be safe plain text");
        }
        return normalized;
    }

    private static boolean containsExternalLink(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("http://") || lower.contains("https://") || lower.contains("www.");
    }
}
