package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedingRunTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

    @Test
    void keepsIdentityStableAcrossRetryAndCompletion() {
        FeedingRun processing = processing();
        FeedingRun failed = processing.failed(
                "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗", NOW.plusSeconds(1));
        FeedingRun restarted = failed.restarted(NOW.plusSeconds(2));
        FeedingRun completed = restarted.completed(31L, NOW.plusSeconds(3));

        assertThat(restarted.status()).isEqualTo(FeedingRunStatus.PROCESSING);
        assertThat(restarted.attemptCount()).isEqualTo(2);
        assertThat(restarted.revision()).isEqualTo(2L);
        assertThat(completed.status()).isEqualTo(FeedingRunStatus.COMPLETED);
        assertThat(completed.resultGrowthRecordId()).isEqualTo(31L);
        assertThat(completed.idempotencyKey()).isEqualTo(processing.idempotencyKey());
        assertThat(completed.correlationId()).isEqualTo(processing.correlationId());
        assertThat(completed.safeErrorCode()).isEqualTo("NUTRITION_FAILED");
        assertThat(completed.safeErrorTime()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectedRunCannotBecomeCompletedOrRestarted() {
        FeedingRun rejected = processing().rejected(
                "PICTURE_UNAVAILABLE", "图片不可用或无权访问", NOW.plusSeconds(1));

        assertThatThrownBy(() -> rejected.completed(31L, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rejected.restarted(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsIllegalDomainValues() {
        assertThatThrownBy(() -> new FeedingContext(false, -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(-1L, TraitDelta.zero(), Map.of(), "演示"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionTraits(
                new BigDecimal("100.01"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Companion.awaken(0L, CompanionBalance.v1()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedingRun.processing(
                11L, 7L, 102L, "UPPERCASE-KEY-0001",
                "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesRunStateAndSafeErrorShape() {
        FeedingRun processing = processing();
        assertThatThrownBy(() -> processing.completed(0L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing.completed(31L, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing.failed("", "message", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L,
                processing.idempotencyKey(), processing.requestFingerprint(), processing.correlationId(),
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, "CODE", null, null, 1, 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L,
                processing.idempotencyKey(), processing.requestFingerprint(), "bad-uuid",
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingRun(21L, 11L, 7L, 102L,
                processing.idempotencyKey(), "A".repeat(64), processing.correlationId(),
                FeedingRunStatus.PROCESSING, NutritionMode.DEMO_DETERMINISTIC, false,
                null, null, null, null, 1, 0L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsContradictoryPersistedStatusCombinations() {
        FeedingRun processing = processing();

        assertThatThrownBy(() -> restored(processing, FeedingRunStatus.COMPLETED,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed");
        assertThatThrownBy(() -> restored(processing, FeedingRunStatus.PROCESSING,
                31L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result");
        assertThatThrownBy(() -> restored(processing, FeedingRunStatus.FAILED,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error");
        assertThatThrownBy(() -> restored(processing, FeedingRunStatus.REJECTED,
                null, "PICTURE_UNAVAILABLE", "图片不可用或无权访问", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error");
    }

    private FeedingRun restored(FeedingRun source, FeedingRunStatus status, Long resultId,
                                String errorCode, String errorMessage, Instant errorTime) {
        return new FeedingRun(source.id(), source.companionId(), source.subjectId(), source.pictureId(),
                source.idempotencyKey(), source.requestFingerprint(), source.correlationId(), status,
                source.nutritionMode(), source.contentUnderstood(), resultId,
                errorCode, errorMessage, errorTime, source.attemptCount(), source.revision(),
                source.createdAt(), source.updatedAt());
    }

    private FeedingRun processing() {
        return FeedingRun.processing(
                11L, 7L, 102L,
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
    }
}
