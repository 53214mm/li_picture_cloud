package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelUsageRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:30:00Z");
    private static final String CORRELATION = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

    @Test
    void successFactoryCarriesNoErrorCode() {
        ModelUsageRecord record = ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW);

        assertThat(record.id()).isNull();
        assertThat(record.success()).isTrue();
        assertThat(record.safeErrorCode()).isNull();
        assertThat(record.connectionId()).isEqualTo(11L);
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.createdTime()).isEqualTo(NOW);
    }

    @Test
    void failureFactoryRequiresSafeErrorCode() {
        ModelUsageRecord record = ModelUsageRecord.failure(7L, ModelTask.VISION_UNDERSTANDING,
                null, ModelProvider.DASHSCOPE, "qwen-vl-max", CostSource.PLATFORM,
                "UPSTREAM_TIMEOUT", CORRELATION, NOW);

        assertThat(record.success()).isFalse();
        assertThat(record.safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");
        assertThat(record.connectionId()).isNull();

        assertThatThrownBy(() -> ModelUsageRecord.failure(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, null, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.failure(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, "bad code",
                CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successRejectsErrorCodeAndMixedIdentityFields() {
        assertThatThrownBy(() -> new ModelUsageRecord(null, 7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, true, "EXTRA_CODE",
                CORRELATION, NOW))
                .describedAs("success 记录不允许携带错误码")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelUsageRecord(0L, 7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, true, null,
                CORRELATION, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(0L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, null, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 0L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                null, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek chat", CostSource.BYOK, CORRELATION, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", null, CORRELATION, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBadCorrelationIdsAndTimes() {
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, "not-a-uuid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        ModelUsageRecord created = ModelUsageRecord.success(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, CORRELATION, NOW);

        ModelUsageRecord persisted = created.withId(11L);
        assertThat(persisted.id()).isEqualTo(11L);

        assertThatThrownBy(() -> persisted.withId(12L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
