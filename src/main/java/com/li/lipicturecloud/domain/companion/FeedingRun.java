package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 一次可重试的喂养请求状态机，而非成长记录本身。
 *
 * <p>同一个幂等键始终对应同一行：网络超时后的重发可以返回原结果，不会再次给伙伴加经验。</p>
 */
public record FeedingRun(
        Long id,
        long companionId,
        long subjectId,
        long pictureId,
        String idempotencyKey,
        String requestFingerprint,
        String correlationId,
        FeedingRunStatus status,
        NutritionPolicy requestedPolicy,
        String requestedProviderCode,
        String requestedModelCode,
        Long resultGrowthRecordId,
        String safeErrorCode,
        String safeErrorMessage,
        Instant safeErrorTime,
        int attemptCount,
        long revision,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[a-z0-9_-]{16,64}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REQUESTED_CODE = Pattern.compile("[a-zA-Z0-9._-]{1,128}");

    public FeedingRun {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (companionId <= 0 || subjectId <= 0 || pictureId <= 0 || attemptCount < 1 || revision < 0) {
            throw new IllegalArgumentException("invalid feeding run state");
        }
        validateIdempotencyKey(idempotencyKey);
        validateFingerprint(requestFingerprint);
        validateCorrelationId(correlationId);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedPolicy, "requestedPolicy");
        validateRequestedModel(requestedPolicy, requestedProviderCode, requestedModelCode);
        if (resultGrowthRecordId != null && resultGrowthRecordId <= 0) {
            throw new IllegalArgumentException("growth record id must be positive");
        }
        validateSafeError(safeErrorCode, safeErrorMessage, safeErrorTime);
        // 状态与附带字段一起构成不变量，防止把“完成但没有回执”之类的半成品读到接口层。
        validateStatusShape(status, resultGrowthRecordId, safeErrorCode);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
    }

    public static FeedingRun processing(long companionId, long subjectId, long pictureId,
                                        String idempotencyKey, String requestFingerprint,
                                        String correlationId, NutritionPolicy requestedPolicy,
                                        String requestedProviderCode, String requestedModelCode, Instant now) {
        return new FeedingRun(null, companionId, subjectId, pictureId, idempotencyKey,
                requestFingerprint, correlationId, FeedingRunStatus.PROCESSING, requestedPolicy,
                requestedProviderCode, requestedModelCode, null, null, null, null, 1, 0L, now, now);
    }

    /** Compatibility entry point for the original deterministic analyzers. */
    public static FeedingRun processing(long companionId, long subjectId, long pictureId,
                                        String idempotencyKey, String requestFingerprint,
                                        String correlationId, NutritionMode mode,
                                        boolean contentUnderstood, Instant now) {
        if (contentUnderstood) {
            throw new IllegalArgumentException("legacy run cannot claim content understanding");
        }
        return processing(companionId, subjectId, pictureId, idempotencyKey, requestFingerprint,
                correlationId, NutritionPolicy.fromLegacyMode(mode), null, null, now);
    }

    /** Compatibility constructor for historical deterministic run rows and fixtures. */
    public FeedingRun(Long id, long companionId, long subjectId, long pictureId,
                      String idempotencyKey, String requestFingerprint, String correlationId,
                      FeedingRunStatus status, NutritionMode mode, boolean contentUnderstood,
                      Long resultGrowthRecordId, String safeErrorCode, String safeErrorMessage,
                      Instant safeErrorTime, int attemptCount, long revision,
                      Instant createdAt, Instant updatedAt) {
        this(id, companionId, subjectId, pictureId, idempotencyKey, requestFingerprint, correlationId,
                status, legacyRequestedPolicy(mode, contentUnderstood), null, null,
                resultGrowthRecordId, safeErrorCode, safeErrorMessage, safeErrorTime, attemptCount,
                revision, createdAt, updatedAt);
    }

    public FeedingRun persistedAs(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return copy(persistedId, status, resultGrowthRecordId, safeErrorCode, safeErrorMessage,
                safeErrorTime, attemptCount, revision, updatedAt);
    }

    public FeedingRun restarted(Instant now) {
        if (status != FeedingRunStatus.FAILED && status != FeedingRunStatus.PROCESSING) {
            throw new IllegalStateException("only failed or processing runs can restart");
        }
        // 保留最后一次失败信息，便于之后审计“这次成功前曾经发生过什么”。
        return copy(id, FeedingRunStatus.PROCESSING, resultGrowthRecordId, safeErrorCode,
                safeErrorMessage, safeErrorTime, Math.addExact(attemptCount, 1),
                Math.addExact(revision, 1L), requireTransitionTime(now));
    }

    public FeedingRun completed(long growthRecordId, Instant now) {
        requireStatus(FeedingRunStatus.PROCESSING);
        if (growthRecordId <= 0) {
            throw new IllegalArgumentException("growth record id must be positive");
        }
        return copy(id, FeedingRunStatus.COMPLETED, growthRecordId, safeErrorCode,
                safeErrorMessage, safeErrorTime, attemptCount, Math.addExact(revision, 1L),
                requireTransitionTime(now));
    }

    public FeedingRun failed(String safeCode, String safeMessage, Instant now) {
        requireStatus(FeedingRunStatus.PROCESSING);
        Instant failureTime = requireTransitionTime(now);
        validateSafeError(safeCode, safeMessage, failureTime);
        return copy(id, FeedingRunStatus.FAILED, null, safeCode, safeMessage, failureTime,
                attemptCount, Math.addExact(revision, 1L), failureTime);
    }

    public FeedingRun rejected(String safeCode, String safeMessage, Instant now) {
        requireStatus(FeedingRunStatus.PROCESSING);
        Instant rejectionTime = requireTransitionTime(now);
        validateSafeError(safeCode, safeMessage, rejectionTime);
        return copy(id, FeedingRunStatus.REJECTED, null, safeCode, safeMessage, rejectionTime,
                attemptCount, Math.addExact(revision, 1L), rejectionTime);
    }

    private FeedingRun copy(Long copyId, FeedingRunStatus copyStatus, Long growthRecordId,
                            String errorCode, String errorMessage, Instant errorTime,
                            int copyAttemptCount, long copyRevision, Instant copyUpdatedAt) {
        return new FeedingRun(copyId, companionId, subjectId, pictureId, idempotencyKey,
                requestFingerprint, correlationId, copyStatus, requestedPolicy, requestedProviderCode,
                requestedModelCode,
                growthRecordId, errorCode, errorMessage, errorTime, copyAttemptCount,
                copyRevision, createdAt, copyUpdatedAt);
    }

    private void requireStatus(FeedingRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("illegal feeding run transition from " + status);
        }
    }

    private Instant requireTransitionTime(Instant now) {
        Instant checkedNow = Objects.requireNonNull(now, "now");
        if (checkedNow.isBefore(updatedAt)) {
            throw new IllegalArgumentException("transition time cannot move backward");
        }
        return checkedNow;
    }

    private static void validateIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("idempotency key must be lowercase and 16-64 characters");
        }
    }

    private static void validateFingerprint(String value) {
        if (value == null || !FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException("request fingerprint must be 64 lowercase hexadecimal characters");
        }
    }

    private static void validateCorrelationId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("correlation id is required");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("correlation id must be a canonical UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("correlation id must be a canonical UUID", exception);
        }
    }

    private static void validateRequestedModel(NutritionPolicy policy, String providerCode, String modelCode) {
        boolean providerPresent = providerCode != null;
        boolean modelPresent = modelCode != null;
        if (providerPresent != modelPresent) {
            throw new IllegalArgumentException("requested provider and model must be specified together");
        }
        if (providerPresent && (!REQUESTED_CODE.matcher(providerCode).matches()
                || !REQUESTED_CODE.matcher(modelCode).matches())) {
            throw new IllegalArgumentException("requested provider and model have invalid format");
        }
        if (policy == NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK && !providerPresent) {
            throw new IllegalArgumentException("visual policy requires requested provider and model");
        }
        if (policy != NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK && providerPresent) {
            throw new IllegalArgumentException("deterministic policy cannot carry requested provider or model");
        }
    }

    private static NutritionPolicy legacyRequestedPolicy(NutritionMode mode, boolean contentUnderstood) {
        if (contentUnderstood) {
            throw new IllegalArgumentException("legacy run cannot claim content understanding");
        }
        return NutritionPolicy.fromLegacyMode(mode);
    }

    /** @deprecated A run stores a request policy, not an actual analysis mode. */
    @Deprecated(forRemoval = false)
    public NutritionMode nutritionMode() {
        return switch (requestedPolicy) {
            case DEMO_ONLY -> NutritionMode.DEMO_DETERMINISTIC;
            case METADATA_ONLY -> NutritionMode.METADATA_DETERMINISTIC;
            case VISUAL_WITH_METADATA_FALLBACK -> NutritionMode.VISUAL_MODEL;
        };
    }

    /** @deprecated Actual content understanding exists only on a completed growth provenance. */
    @Deprecated(forRemoval = false)
    public boolean contentUnderstood() {
        return false;
    }

    private static void validateSafeError(String code, String message, Instant time) {
        boolean allNull = code == null && message == null && time == null;
        boolean allPresent = code != null && message != null && time != null;
        if (!allNull && !allPresent) {
            throw new IllegalArgumentException("safe error fields must be all present or all absent");
        }
        if (allPresent && (code.isBlank() || message.isBlank())) {
            throw new IllegalArgumentException("safe error code and message must not be blank");
        }
    }

    private static void validateStatusShape(FeedingRunStatus status, Long resultGrowthRecordId,
                                            String safeErrorCode) {
        if (status == FeedingRunStatus.COMPLETED && resultGrowthRecordId == null) {
            throw new IllegalArgumentException("completed feeding run requires a result growth record");
        }
        if (status != FeedingRunStatus.COMPLETED && resultGrowthRecordId != null) {
            throw new IllegalArgumentException("only a completed feeding run may carry a result growth record");
        }
        if ((status == FeedingRunStatus.FAILED || status == FeedingRunStatus.REJECTED)
                && safeErrorCode == null) {
            throw new IllegalArgumentException("failed or rejected feeding run requires a safe error");
        }
    }
}
