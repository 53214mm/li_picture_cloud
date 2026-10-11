package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.domain.airuntime.VisionSearchPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionSearchPreferenceServiceTest {

    private VisionSearchPreferenceRepository repository;
    private TaskRoutingRuleRepository routing;
    private ModelConnectionRepository connections;
    private CompanionFeatureProperties properties;
    private VisionSearchPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = mock(VisionSearchPreferenceRepository.class);
        routing = mock(TaskRoutingRuleRepository.class);
        connections = mock(ModelConnectionRepository.class);
        properties = new CompanionFeatureProperties();
        when(routing.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING)).thenReturn(Optional.empty());
        service = new VisionSearchPreferenceService(repository, routing, connections, properties);
    }

    @Test
    void platformQwenSupportsSearchAndReportsTheModel() {
        when(repository.forcedSearch(7L)).thenReturn(true);
        assertThat(service.view(7L).supported()).isTrue();
        assertThat(service.view(7L).forcedSearch()).isTrue();
        assertThat(service.view(7L).modelCode()).isEqualTo("qwen3.8-max");
        assertThat(service.forcedSearch(7L)).isTrue();
    }

    @Test
    void ownNonDashScopeVisionConnectionKeepsTheSwitchButMarksItUnsupported() {
        when(routing.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING))
                .thenReturn(Optional.of(TaskRoutingRule.restore(3L, 7L, ModelTask.VISION_UNDERSTANDING, 9L, 1L)));
        when(connections.findById(9L)).thenReturn(Optional.of(ModelConnection.restore(9L, 7L, ModelProvider.OPENAI,
                "OpenAI", URI.create("https://api.openai.com/v1"), "gpt-4o", 5L, true, 1L)));

        assertThat(service.view(7L).supported()).isFalse();
        assertThat(service.view(7L).modelCode()).isEqualTo("gpt-4o");
    }

    @Test
    void choosePersistsTheSwitch() {
        service.choose(7L, true);
        verify(repository).save(7L, true);
        assertThat(service.forcedSearch(0L)).isFalse();
    }
}
