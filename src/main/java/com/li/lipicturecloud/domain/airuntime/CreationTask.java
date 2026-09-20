package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一次图片炼金创作任务。状态机转移是显式方法，每次转移 revision 恰好 +1；
 * 文本字段只接受安全纯文本（无控制字符）。原图永不覆盖，作品是新的内容。
 */
public record CreationTask(
        Long id,
        long subjectId,
        CreationKind kind,
        List<Long> sourcePictureIds,
        CreationStatus status,
        String outlineText,
        String draftText,
        String resultText,
        Long modelConnectionId,
        String idempotencyKey,
        long revision,
        Instant createdTime,
        Instant updatedTime) {

    public static final int MAX_SOURCE_PICTURES = 12;
    public static final int MAX_OUTLINE_CODE_POINTS = 1000;
    public static final int MAX_DRAFT_CODE_POINTS = 4000;
    public static final int MAX_RESULT_CODE_POINTS = 8000;

    public CreationTask {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid creation task identity or revision");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        if (sourcePictureIds == null || sourcePictureIds.isEmpty()) {
            throw new IllegalArgumentException("creation task requires at least one source picture");
        }
        if (sourcePictureIds.size() > MAX_SOURCE_PICTURES) {
            throw new IllegalArgumentException("too many source pictures");
        }
        Set<Long> unique = new LinkedHashSet<>(sourcePictureIds);
        if (unique.size() != sourcePictureIds.size()) {
            throw new IllegalArgumentException("source pictures must not repeat");
        }
        for (Long pictureId : sourcePictureIds) {
            if (pictureId == null || pictureId <= 0) {
                throw new IllegalArgumentException("source picture ids must be positive");
            }
        }
        outlineText = requireSafeText(outlineText, MAX_OUTLINE_CODE_POINTS, "outlineText");
        draftText = requireSafeText(draftText, MAX_DRAFT_CODE_POINTS, "draftText");
        resultText = requireSafeText(resultText, MAX_RESULT_CODE_POINTS, "resultText");
        if (modelConnectionId != null && modelConnectionId <= 0) {
            throw new IllegalArgumentException("modelConnectionId must be positive or null");
        }
        if (idempotencyKey == null || idempotencyKey.length() != 36) {
            throw new IllegalArgumentException("idempotencyKey must be a UUID string");
        }
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (updatedTime.isBefore(createdTime)) {
            throw new IllegalArgumentException("updatedTime must not precede createdTime");
        }
    }

    public static CreationTask create(long subjectId, CreationKind kind,
                                      List<Long> sourcePictureIds, String idempotencyKey,
                                      Instant now) {
        return new CreationTask(null, subjectId, kind, List.copyOf(sourcePictureIds),
                CreationStatus.PENDING, null, null, null, null, idempotencyKey, 0L, now, now);
    }

    public CreationTask withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CreationTask(persistedId, subjectId, kind, sourcePictureIds, status,
                outlineText, draftText, resultText, modelConnectionId, idempotencyKey,
                revision, createdTime, updatedTime);
    }

    public CreationTask startOutlining(Instant now) {
        requireStatus(CreationStatus.PENDING, "startOutlining");
        return advance(CreationStatus.OUTLINING, outlineText, draftText, resultText, now);
    }

    public CreationTask completeOutline(String outline, Long modelConnectionId, Instant now) {
        requireStatus(CreationStatus.OUTLINING, "completeOutline");
        return advance(CreationStatus.AWAITING_CONFIRM, requireSafeText(outline,
                MAX_OUTLINE_CODE_POINTS, "outline"), null, null, now, modelConnectionId);
    }

    public CreationTask confirmOutline(Instant now) {
        requireStatus(CreationStatus.AWAITING_CONFIRM, "confirmOutline");
        if (outlineText == null || draftText != null) {
            throw new IllegalStateException("confirmOutline requires an outline awaiting confirmation");
        }
        return advance(CreationStatus.DRAFTING, outlineText, null, null, now);
    }

    public CreationTask completeDraft(String draft, Instant now) {
        requireStatus(CreationStatus.DRAFTING, "completeDraft");
        return advance(CreationStatus.AWAITING_CONFIRM, outlineText, requireSafeText(draft,
                MAX_DRAFT_CODE_POINTS, "draft"), null, now);
    }

    public CreationTask confirmDraft(Instant now) {
        requireStatus(CreationStatus.AWAITING_CONFIRM, "confirmDraft");
        if (draftText == null) {
            throw new IllegalStateException("confirmDraft requires a draft awaiting confirmation");
        }
        return advance(CreationStatus.SAVING, outlineText, draftText, null, now);
    }

    /** 表情草稿等候选式玩法：从候选列表选中一条作为作品草稿进入保存。 */
    public CreationTask selectDraft(String draft, Instant now) {
        requireStatus(CreationStatus.AWAITING_CONFIRM, "selectDraft");
        if (outlineText != null || draftText != null) {
            throw new IllegalStateException("selectDraft requires a candidate-style task awaiting selection");
        }
        return advance(CreationStatus.SAVING, null, requireSafeText(draft,
                MAX_DRAFT_CODE_POINTS, "draft"), null, now);
    }

    /** 融合生成完成：图片字节存于专用暂存表，任务进入等待确认（无文本草稿）。 */
    public CreationTask completeFusion(Long modelConnectionId, Instant now) {
        requireKind(CreationKind.IMAGE_FUSION, "completeFusion");
        requireStatus(CreationStatus.OUTLINING, "completeFusion");
        return advance(CreationStatus.AWAITING_CONFIRM, null, null, null, now, modelConnectionId);
    }

    /** 用户确认融合结果，进入保存（目标空间与可见性在保存时确认）。 */
    public CreationTask confirmFusion(Instant now) {
        requireKind(CreationKind.IMAGE_FUSION, "confirmFusion");
        requireStatus(CreationStatus.AWAITING_CONFIRM, "confirmFusion");
        if (outlineText != null || draftText != null) {
            throw new IllegalStateException("confirmFusion requires a fusion task awaiting confirmation");
        }
        return advance(CreationStatus.SAVING, null, null, null, now);
    }

    /** 融合作品回库完成：结果图片 ID 写入 resultText（血缘另行写入 resultPictureId）。 */
    public CreationTask completeFusionSave(long resultPictureId, Instant now) {
        requireKind(CreationKind.IMAGE_FUSION, "completeFusionSave");
        requireStatus(CreationStatus.SAVING, "completeFusionSave");
        if (resultPictureId <= 0) {
            throw new IllegalArgumentException("resultPictureId must be positive");
        }
        return advance(CreationStatus.SAVED, null, null, Long.toString(resultPictureId), now);
    }

    public CreationTask completeSave(String result, Instant now) {
        requireStatus(CreationStatus.SAVING, "completeSave");
        return advance(CreationStatus.SAVED, outlineText, draftText, requireSafeText(result,
                MAX_RESULT_CODE_POINTS, "result"), now);
    }

    public CreationTask fail(Instant now) {
        if (isTerminal()) {
            throw new IllegalStateException("terminal creation tasks cannot fail");
        }
        return advance(CreationStatus.FAILED, outlineText, draftText, resultText, now);
    }

    public CreationTask expire(Instant now) {
        requireStatus(CreationStatus.AWAITING_CONFIRM, "expire");
        return advance(CreationStatus.EXPIRED, outlineText, draftText, resultText, now);
    }

    public boolean isTerminal() {
        return status == CreationStatus.SAVED || status == CreationStatus.FAILED
                || status == CreationStatus.EXPIRED;
    }

    private void requireStatus(CreationStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException(operation + " requires " + expected
                    + " but task is " + status);
        }
    }

    private void requireKind(CreationKind expected, String operation) {
        if (kind != expected) {
            throw new IllegalStateException(operation + " requires a " + expected
                    + " task but this is " + kind);
        }
    }

    private CreationTask advance(CreationStatus next, String outline, String draft,
                                 String result, Instant now) {
        return new CreationTask(id, subjectId, kind, sourcePictureIds, next, outline, draft,
                result, modelConnectionId, idempotencyKey, Math.addExact(revision, 1L),
                createdTime, now);
    }

    private CreationTask advance(CreationStatus next, String outline, String draft,
                                 String result, Instant now, Long nextConnectionId) {
        return new CreationTask(id, subjectId, kind, sourcePictureIds, next, outline, draft,
                result, nextConnectionId, idempotencyKey, Math.addExact(revision, 1L),
                createdTime, now);
    }

    private static String requireSafeText(String value, int maxCodePoints, String field) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        // 排版空白是安全纯文本的组成部分（前端 pre-wrap 展示）：统一换行风格后放行，
        // 其余控制字符（含双向/分隔等技巧字符）一律拒绝。
        String normalized = stripped.replace("\r\n", "\n").replace('\r', '\n');
        int length = normalized.codePointCount(0, normalized.length());
        if (length > maxCodePoints) {
            throw new IllegalArgumentException(field + " exceeds " + maxCodePoints + " characters");
        }
        if (normalized.codePoints().anyMatch(codePoint ->
                codePoint != '\n' && codePoint != '\t' && Character.isISOControl(codePoint))) {
            throw new IllegalArgumentException(field + " must be safe plain text");
        }
        return normalized;
    }
}
