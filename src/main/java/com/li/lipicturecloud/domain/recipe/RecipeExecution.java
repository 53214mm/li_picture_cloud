package com.li.lipicturecloud.domain.recipe;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次配方执行的回放记录：触发时间、命中条件快照、费用报价、创作任务引用与安全错误码。
 * 回放只含安全字段：不含图片字节、提示词正文、密钥或用户原文。
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
        Long creationTaskId,
        String safeErrorCode,
        Instant createdTime) {

    public static final int MAX_JSON_CODE_POINTS = 4000;
    public static final int MAX_QUOTE_CODE_POINTS = 2000;
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[a-zA-Z0-9._\\-]{1,64}");

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
                throw new IllegalArgumentException("DRY_RUN execution must not carry a task id or error code");
            }
        }
        Objects.requireNonNull(triggeredTime, "triggeredTime");
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static RecipeExecution dryRun(long recipeId, int recipeVersion, long subjectId,
                                         Instant triggeredTime, String matchedJson,
                                         String quoteJson, Instant now) {
        return new RecipeExecution(null, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.DRY_RUN, triggeredTime, matchedJson, quoteJson,
                null, null, now);
    }

    public static RecipeExecution executed(long recipeId, int recipeVersion, long subjectId,
                                           Instant triggeredTime, String matchedJson,
                                           String quoteJson, long creationTaskId, Instant now) {
        return new RecipeExecution(null, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.EXECUTED, triggeredTime, matchedJson, quoteJson,
                creationTaskId, null, now);
    }

    public static RecipeExecution restore(Long id, long recipeId, int recipeVersion,
                                          long subjectId, RecipeExecutionStatus status,
                                          Instant triggeredTime, String matchedJson,
                                          String quoteJson, Long creationTaskId,
                                          String safeErrorCode, Instant createdTime) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId, status, triggeredTime,
                matchedJson, quoteJson, creationTaskId, safeErrorCode, createdTime);
    }

    public RecipeExecution withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new RecipeExecution(persistedId, recipeId, recipeVersion, subjectId, status,
                triggeredTime, matchedJson, quoteJson, creationTaskId, safeErrorCode, createdTime);
    }

    /** 试运行结束并产生真实创作任务（DRY_RUN → EXECUTED，终态）。 */
    public RecipeExecution complete(long taskId, Instant now) {
        requireDryRun("complete");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.EXECUTED, triggeredTime, matchedJson, quoteJson,
                requirePositiveTaskId(taskId), null, Objects.requireNonNull(now, "now"));
    }

    /** 执行失败（DRY_RUN → FAILED，终态），只携带安全错误码。 */
    public RecipeExecution fail(String errorCode, Instant now) {
        requireDryRun("fail");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.FAILED, triggeredTime, matchedJson, quoteJson,
                null, checkErrorCode(errorCode), Objects.requireNonNull(now, "now"));
    }

    /** 条件未命中/守门拒绝（DRY_RUN → REJECTED，终态）。 */
    public RecipeExecution reject(String errorCode, Instant now) {
        requireDryRun("reject");
        return new RecipeExecution(id, recipeId, recipeVersion, subjectId,
                RecipeExecutionStatus.REJECTED, triggeredTime, matchedJson, quoteJson,
                null, checkErrorCode(errorCode), Objects.requireNonNull(now, "now"));
    }

    public boolean isTerminal() {
        return status != RecipeExecutionStatus.DRY_RUN;
    }

    private void requireDryRun(String operation) {
        if (status != RecipeExecutionStatus.DRY_RUN) {
            throw new IllegalStateException(operation + " requires DRY_RUN but execution is " + status);
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
