package com.li.lipicturecloud.domain.recipe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次配方执行的回放记录：触发时间、命中条件快照、费用报价、来源图片快照、
 * 创作任务引用与安全错误码。
 *
 * <p>回放只含安全字段：不含图片字节、提示词正文、密钥或用户原文。来源图片快照
 * （{@code sourcePictureIdsJson}）把"预览"与"确认执行"绑定在同一组图片上：
 * 用户改选图片后必须重新试运行，不能拿旧预览确认另一组图片。</p>
 */
public record RecipeExecution(
        Long id,
        long recipeId,
        int recipeVersion,
        long subjectId,
        RecipeExecutionStatus status,
        Instant triggeredTime,
        String matchedJson,
        String quoteJson,
        String sourcePictureIdsJson,
        String opportunityKey,
        Long creationTaskId,
        String safeErrorCode,
        Instant createdTime) {

    public static final int MAX_JSON_CODE_POINTS = 4000;
    public static final int MAX_QUOTE_CODE_POINTS = 2000;
    public static final int MAX_SOURCE_PICTURES = 12;
    public static final int MAX_KEY_CODE_POINTS = 128;
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[a-zA-Z0-9._\\-]{1,64}");
    private static final Pattern SOURCE_PICTURES = Pattern.compile("\\[(\\d+)(,\\d+)*]");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._:\\-]{1,128}");

    public RecipeExecution {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (recipeId <= 0 || recipeVersion < 1 || subjectId <= 0) {
            throw new IllegalArgumentException("invalid recipe execution identity");
        }
        Objects.requireNonNull(status, "status");
        matchedJson = checkText(matchedJson, MAX_JSON_CODE_POINTS, "matchedJson");
        quoteJson = checkText(quoteJson, MAX_QUOTE_CODE_POINTS, "quoteJson");
        sourcePictureIdsJson = checkSourcePictures(sourcePictureIdsJson);
        opportunityKey = checkKey(opportunityKey);
        if (status == RecipeExecutionStatus.EXECUTED) {
            if (creationTaskId == null || creationTaskId <= 0) {
                throw new IllegalArgumentException("EXECUTED execution requires a creation task id");
            }
            if (safeErrorCode != null) {
                throw new IllegalArgumentException("EXECUTED execution must not carry an error code");
            }
        } else if (status == RecipeExecutionStatus.FAILED || status == RecipeExecutionStatus.REJECTED) {
            if (creationTaskId != null) {
                throw new IllegalArgumentException("failed/rejected execution must not carry a task id");
            }
            safeErrorCode = checkErrorCode(safeErrorCode);
        } else {
            if (creationTaskId != null || safeErrorCode != null) {
                throw new IllegalArgumentException(
                        status + " execution must not carry a task id or error code");
            }
        }
        Objects.requireNonNull(triggeredTime, "triggeredTime");
        Objects.requireNonNull(createdTime, "createdTime");
    }

    /** 试运行记录（DRY_RUN）：绑定本次预览使用的来源图片快照。 */
    public static RecipeExecution dryRun(long recipeId, int recipeVersion, long subjectId,
                                         Instant triggeredTime, String matchedJson,
                                         String quoteJson, String sourcePictureIdsJson,
                                         Instant now) {
        return new RecipeExecution(null, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.DRY_RUN, triggeredTime, matchedJson, quoteJson,
                sourcePictureIdsJson, null, null, null, now);
    }

    /**
     * 机会触发的待确认记录（PENDING_CONFIRM）：阶段 3 机会观察 + 契约/频率/安静时段守门
     * 通过后由 WHEN 命中生成，{@code opportunityKey} 是同一机会窗口的去重键。
     */
    public static RecipeExecution pending(long recipeId, int recipeVersion, long subjectId,
                                          Instant triggeredTime, String matchedJson,
                                          String quoteJson, String sourcePictureIdsJson,
                                          String opportunityKey, Instant now) {
        return new RecipeExecution(null, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.PENDING_CONFIRM, triggeredTime, matchedJson, quoteJson,
                sourcePictureIdsJson, opportunityKey, null, null, now);
    }

    public static RecipeExecution executed(long recipeId, int recipeVersion, long subjectId,
                                           Instant triggeredTime, String matchedJson,
                                           String quoteJson, String sourcePictureIdsJson,
                                           long creationTaskId, Instant now) {
        return new RecipeExecution(null, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.EXECUTED, triggeredTime, matchedJson, quoteJson,
                sourcePictureIdsJson, null, creationTaskId, null, now);
    }

    public static RecipeExecution restore(Long id, long recipeId, int recipeVersion,
                                          long subjectId, RecipeExecutionStatus status,
                                          Instant triggeredTime, String matchedJson,
                                          String quoteJson, String sourcePictureIdsJson,
                                          String opportunityKey, Long creationTaskId,
                                          String safeErrorCode, Instant createdTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId, status, triggeredTime,
                matchedJson, quoteJson, sourcePictureIdsJson, opportunityKey, creationTaskId,
                safeErrorCode, createdTime);
    }

    public RecipeExecution withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new RecipeExecution(persistedId, recipeId, recipeVersion, subjectId, status,
                triggeredTime, matchedJson, quoteJson, sourcePictureIdsJson, opportunityKey,
                creationTaskId, safeErrorCode, createdTime);
    }

    /** 快照的来源图片 ID（快照缺失或历史记录为空时返回空列表）。 */
    public List<Long> sourcePictureIds() {
        if (sourcePictureIdsJson == null) {
            return List.of();
        }
        String body = sourcePictureIdsJson.substring(1, sourcePictureIdsJson.length() - 1);
        if (body.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(body.split(",")).map(Long::valueOf).toList();
    }

    /** 把来源图片 ID 渲染成快照 JSON（按传入顺序去重，最多 12 张）。 */
    public static String snapshotJson(List<Long> pictureIds) {
        Objects.requireNonNull(pictureIds, "pictureIds");
        List<Long> distinct = pictureIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty() || distinct.size() > MAX_SOURCE_PICTURES) {
            throw new IllegalArgumentException("snapshot requires 1-" + MAX_SOURCE_PICTURES + " pictures");
        }
        for (Long pictureId : distinct) {
            if (pictureId <= 0) {
                throw new IllegalArgumentException("snapshot picture ids must be positive");
            }
        }
        return distinct.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    /** 确认执行并产生真实创作任务（DRY_RUN/PENDING_CONFIRM → EXECUTED，终态）。 */
    public RecipeExecution complete(long taskId, String matchedSnapshot, String quoteSnapshot,
                                    Instant now) {
        requireConfirmable("complete");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.EXECUTED, triggeredTime,
                checkText(matchedSnapshot, MAX_JSON_CODE_POINTS, "matchedJson"),
                checkText(quoteSnapshot, MAX_QUOTE_CODE_POINTS, "quoteJson"),
                sourcePictureIdsJson, opportunityKey,
                requirePositiveTaskId(taskId), null, Objects.requireNonNull(now, "now"));
    }

    /** 执行失败（DRY_RUN/PENDING_CONFIRM → FAILED，终态），只携带安全错误码。 */
    public RecipeExecution fail(String errorCode, String matchedSnapshot, String quoteSnapshot,
                                Instant now) {
        requireConfirmable("fail");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.FAILED, triggeredTime,
                checkText(matchedSnapshot, MAX_JSON_CODE_POINTS, "matchedJson"),
                checkText(quoteSnapshot, MAX_QUOTE_CODE_POINTS, "quoteJson"),
                sourcePictureIdsJson, opportunityKey,
                null, checkErrorCode(errorCode), Objects.requireNonNull(now, "now"));
    }

    /** 条件未命中/守门拒绝（DRY_RUN/PENDING_CONFIRM → REJECTED，终态）。 */
    public RecipeExecution reject(String errorCode, String matchedSnapshot, String quoteSnapshot,
                                  Instant now) {
        requireConfirmable("reject");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.REJECTED, triggeredTime,
                checkText(matchedSnapshot, MAX_JSON_CODE_POINTS, "matchedJson"),
                checkText(quoteSnapshot, MAX_QUOTE_CODE_POINTS, "quoteJson"),
                sourcePictureIdsJson, opportunityKey,
                null, checkErrorCode(errorCode), Objects.requireNonNull(now, "now"));
    }

    public boolean isTerminal() {
        return status != RecipeExecutionStatus.DRY_RUN
                && status != RecipeExecutionStatus.PENDING_CONFIRM;
    }

    public boolean isAwaitingConfirm() {
        return !isTerminal();
    }

    private void requireConfirmable(String operation) {
        if (!isAwaitingConfirm()) {
            throw new IllegalStateException(operation + " requires DRY_RUN or PENDING_CONFIRM but execution is "
                    + status);
        }
    }

    private static long requirePositiveTaskId(long taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("creation task id must be positive");
        }
        return taskId;
    }

    private static String checkErrorCode(String value) {
        if (value == null || !SAFE_ERROR_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("safeErrorCode must match " + SAFE_ERROR_CODE.pattern());
        }
        return value;
    }

    private static String checkSourcePictures(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!SOURCE_PICTURES.matcher(normalized).matches()
                || normalized.codePointCount(0, normalized.length()) > 512) {
            throw new IllegalArgumentException("sourcePictureIdsJson must be a bounded id array");
        }
        long count = normalized.chars().filter(character -> character == ',').count() + 1;
        if (count > MAX_SOURCE_PICTURES) {
            throw new IllegalArgumentException("sourcePictureIdsJson holds too many pictures");
        }
        return normalized;
    }

    private static String checkKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!SAFE_KEY.matcher(normalized).matches()
                || normalized.codePointCount(0, normalized.length()) > MAX_KEY_CODE_POINTS) {
            throw new IllegalArgumentException("opportunityKey must be a bounded safe key");
        }
        return normalized;
    }

    private static String checkText(String value, int maxCodePoints, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maxCodePoints) {
            throw new IllegalArgumentException(field + " must be 1-" + maxCodePoints + " characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be safe plain text");
        }
        return normalized;
    }
}
