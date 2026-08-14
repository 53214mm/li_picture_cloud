package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfileRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelCapabilityProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private ModelCapabilityProfileRepository profileRepository;
    private ModelCapabilityRegistry registry;
    private ModelCapabilityProfileService service;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ModelCapabilityProfileRepository.class);
        registry = mock(ModelCapabilityRegistry.class);
        service = new ModelCapabilityProfileService(profileRepository, registry,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ModelConnection connection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    @Test
    void snapshotAppendsRegistryCapabilitiesAtCurrentTime() {
        ModelCapabilities capabilities = ModelCapabilities.of(true, false, true, true, false,
                false, false, 64_000, ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP);
        when(registry.capabilitiesFor(ModelProvider.DEEPSEEK, "deepseek-chat"))
                .thenReturn(capabilities);
        when(profileRepository.append(any(ModelCapabilityProfile.class))).thenAnswer(invocation ->
                invocation.<ModelCapabilityProfile>getArgument(0).withId(3L));

        ModelCapabilityProfile profile = service.snapshot(connection(), 7L);

        assertThat(profile.id()).isEqualTo(3L);
        assertThat(profile.connectionId()).isEqualTo(9L);
        assertThat(profile.text()).isTrue();
        assertThat(profile.createdTime()).isEqualTo(NOW);
    }

    @Test
    void snapshotRejectsUnpersistedConnections() {
        ModelConnection unpersisted = ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L);
        assertThatThrownBy(() -> service.snapshot(unpersisted, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotRejectsForeignConnections() {
        assertThatThrownBy(() -> service.snapshot(connection(), 8L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own");
    }

    @Test
    void latestFailsWhenNoSnapshotExists() {
        when(profileRepository.findLatestByConnectionId(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.latest(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力画像");
    }
}
