package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCapabilityProfileTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    void snapshotCarriesCapabilitiesAndIdentity() {
        ModelCapabilityProfile profile = ModelCapabilityProfile.snapshot(9L, 7L,
                ModelProvider.DEEPSEEK, "deepseek-chat", ModelCapabilities.of(true, false, true,
                        true, false, false, false, 64_000, ModelCapabilities.SYNC,
                        ModelCapabilities.COST_CHEAP), NOW);

        assertThat(profile.id()).isNull();
        assertThat(profile.connectionId()).isEqualTo(9L);
        assertThat(profile.subjectId()).isEqualTo(7L);
        assertThat(profile.provider()).isEqualTo(ModelProvider.DEEPSEEK);
        assertThat(profile.text()).isTrue();
        assertThat(profile.vision()).isFalse();
        assertThat(profile.maxContextTokens()).isEqualTo(64_000);
        assertThat(profile.createdTime()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidIdentitiesAndFields() {
        assertThatThrownBy(() -> ModelCapabilityProfile.snapshot(0L, 7L, ModelProvider.DEEPSEEK,
                "deepseek-chat", ModelCapabilities.unknown(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelCapabilityProfile.snapshot(9L, 0L, ModelProvider.DEEPSEEK,
                "deepseek-chat", ModelCapabilities.unknown(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelCapabilityProfile.snapshot(9L, 7L, null,
                "deepseek-chat", ModelCapabilities.unknown(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelCapabilityProfile.snapshot(9L, 7L, ModelProvider.DEEPSEEK,
                null, ModelCapabilities.unknown(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ModelCapabilityProfile(1L, 9L, 7L, ModelProvider.DEEPSEEK,
                "deepseek-chat", false, false, false, false, false, false, false, -1,
                "UNKNOWN", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCapabilityProfile(1L, 9L, 7L, ModelProvider.DEEPSEEK,
                "deepseek-chat", false, false, false, false, false, false, false, null,
                "SYNCING", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCapabilityProfile(1L, 9L, 7L, ModelProvider.DEEPSEEK,
                "deepseek-chat", false, false, false, false, false, false, false, null,
                "UNKNOWN", "FREE", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelCapabilityProfile.snapshot(9L, 7L, ModelProvider.DEEPSEEK,
                "deepseek-chat", ModelCapabilities.unknown(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        ModelCapabilityProfile created = ModelCapabilityProfile.snapshot(9L, 7L,
                ModelProvider.DEEPSEEK, "deepseek-chat", ModelCapabilities.unknown(), NOW);

        ModelCapabilityProfile persisted = created.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);

        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
