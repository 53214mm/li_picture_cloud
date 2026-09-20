package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelUsageServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private ModelUsageRecordRepository usageRepository;
    private ModelUsageService service;

    @BeforeEach
    void setUp() {
        usageRepository = mock(ModelUsageRecordRepository.class);
        service = new ModelUsageService(usageRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordSuccessAppendsSafeRecordWithGeneratedCorrelationId() {
        when(usageRepository.append(any(ModelUsageRecord.class))).thenAnswer(invocation ->
                invocation.<ModelUsageRecord>getArgument(0).withId(9L));

        ModelUsageRecord record = service.recordSuccess(7L, ModelTask.LANGUAGE_AGENT, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);

        assertThat(record.id()).isEqualTo(9L);
        assertThat(record.success()).isTrue();
        assertThat(record.safeErrorCode()).isNull();
        assertThat(record.correlationId()).hasSize(36);
        assertThat(record.createdTime()).isEqualTo(NOW);
        assertThat(record.connectionId()).isEqualTo(11L);
    }

    @Test
    void recordFailureAppendsRecordWithSafeErrorCode() {
        when(usageRepository.append(any(ModelUsageRecord.class))).thenAnswer(invocation ->
                invocation.<ModelUsageRecord>getArgument(0).withId(10L));

        ModelUsageRecord record = service.recordFailure(7L, ModelTask.CONNECTIVITY_CHECK, 11L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, "UPSTREAM_TIMEOUT");

        assertThat(record.success()).isFalse();
        assertThat(record.safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");
        assertThat(record.task()).isEqualTo(ModelTask.CONNECTIVITY_CHECK);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> service.recordSuccess(0L, ModelTask.LANGUAGE_AGENT, null,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordSuccess(7L, null, null,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordFailure(7L, ModelTask.LANGUAGE_AGENT, null,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, null))
                .isInstanceOf(NullPointerException.class);
        verify(usageRepository, org.mockito.Mockito.never()).append(any());
    }
}
