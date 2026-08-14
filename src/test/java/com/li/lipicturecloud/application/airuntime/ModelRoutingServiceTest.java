package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelRoutingServiceTest {

    private TaskRoutingRuleRepository routingRepository;
    private ModelConnectionRepository connectionRepository;
    private ModelRoutingService service;

    @BeforeEach
    void setUp() {
        routingRepository = mock(TaskRoutingRuleRepository.class);
        connectionRepository = mock(ModelConnectionRepository.class);
        service = new ModelRoutingService(routingRepository, connectionRepository);
    }

    private ModelConnection connection(long id, long subjectId) {
        return ModelConnection.restore(id, subjectId, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    @Test
    void upsertCreatesNewRuleOrRoutesExistingOne() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.empty());
        when(routingRepository.insert(any(TaskRoutingRule.class))).thenAnswer(invocation ->
                invocation.<TaskRoutingRule>getArgument(0).withId(1L));
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection(9L, 7L)));

        TaskRoutingRule created = service.upsert(7L, ModelTask.LANGUAGE_AGENT, 9L);
        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.connectionId()).isEqualTo(9L);

        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(created));
        when(routingRepository.save(any(TaskRoutingRule.class), eq(0L))).thenReturn(true);
        TaskRoutingRule updated = service.upsert(7L, ModelTask.LANGUAGE_AGENT, null);
        assertThat(updated.connectionId()).isNull();
        assertThat(updated.revision()).isEqualTo(1L);
    }

    @Test
    void upsertRejectsUnroutableTasksAndForeignConnections() {
        assertThatThrownBy(() -> service.upsert(7L, ModelTask.CONNECTIVITY_CHECK, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("探测任务");

        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upsert(7L, ModelTask.LANGUAGE_AGENT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连接不存在");

        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection(9L, 8L)));
        assertThatThrownBy(() -> service.upsert(7L, ModelTask.LANGUAGE_AGENT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连接不存在");

        verify(routingRepository, never()).insert(any());
        assertThatThrownBy(() -> service.upsert(0L, ModelTask.LANGUAGE_AGENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void casConflictSurfacesAsOperationError() {
        TaskRoutingRule existing = TaskRoutingRule.restore(1L, 7L, ModelTask.LANGUAGE_AGENT,
                9L, 0L);
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(existing));
        when(routingRepository.save(any(TaskRoutingRule.class), eq(0L))).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(7L, ModelTask.LANGUAGE_AGENT, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
    }

    @Test
    void lostFirstWriteRaceStillAppliesTheRequestedRouteViaCas() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.empty());
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection(9L, 7L)));
        // 并发首写输了：仓储读回赢家行（connectionId=5），与本次请求的 9 不一致。
        when(routingRepository.insert(any(TaskRoutingRule.class)))
                .thenReturn(TaskRoutingRule.restore(2L, 7L, ModelTask.LANGUAGE_AGENT, 5L, 0L));
        when(routingRepository.save(any(TaskRoutingRule.class), eq(0L))).thenReturn(true);

        TaskRoutingRule result = service.upsert(7L, ModelTask.LANGUAGE_AGENT, 9L);

        assertThat(result.connectionId()).isEqualTo(9L);
        assertThat(result.revision()).isEqualTo(1L);
        verify(routingRepository).save(any(TaskRoutingRule.class), eq(0L));
    }

    @Test
    void deleteRemovesExistingRuleAndToleratesMissingOne() {
        TaskRoutingRule existing = TaskRoutingRule.restore(1L, 7L, ModelTask.LANGUAGE_AGENT,
                9L, 2L);
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(existing));
        when(routingRepository.delete(1L, 2L)).thenReturn(true);
        assertThat(service.delete(7L, ModelTask.LANGUAGE_AGENT)).isTrue();

        when(routingRepository.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING))
                .thenReturn(Optional.empty());
        assertThat(service.delete(7L, ModelTask.VISION_UNDERSTANDING)).isFalse();
    }

    @Test
    void listReturnsAllOwnedRules() {
        TaskRoutingRule rule = TaskRoutingRule.restore(1L, 7L, ModelTask.LANGUAGE_AGENT, 9L, 0L);
        when(routingRepository.findByOwnerId(7L)).thenReturn(List.of(rule));

        assertThat(service.list(7L)).containsExactly(rule);
        assertThatThrownBy(() -> service.list(0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
