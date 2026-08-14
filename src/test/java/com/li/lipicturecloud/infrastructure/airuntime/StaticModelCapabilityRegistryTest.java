package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticModelCapabilityRegistryTest {

    private StaticModelCapabilityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StaticModelCapabilityRegistry();
    }

    @Test
    void knownModelsExposeTheirDesignDefaultCapabilities() {
        ModelCapabilities deepseek = registry.capabilitiesFor(ModelProvider.DEEPSEEK,
                "deepseek-chat");
        assertThat(deepseek.text()).isTrue();
        assertThat(deepseek.vision()).isFalse();
        assertThat(deepseek.toolCall()).isTrue();
        assertThat(deepseek.maxContextTokens()).isEqualTo(64_000);

        ModelCapabilities reasoner = registry.capabilitiesFor(ModelProvider.DEEPSEEK,
                "deepseek-reasoner");
        assertThat(reasoner.reasoning()).isTrue();

        ModelCapabilities qwen = registry.capabilitiesFor(ModelProvider.DASHSCOPE,
                "qwen3.6-flash");
        assertThat(qwen.vision()).isTrue();
        assertThat(qwen.text()).isTrue();
    }

    @Test
    void unknownCombinationsFallBackToUnsupportedWithoutGuessing() {
        ModelCapabilities unknown = registry.capabilitiesFor(ModelProvider.DEEPSEEK,
                "some-new-model");
        assertThat(unknown).isEqualTo(ModelCapabilities.unknown());
        assertThat(registry.capabilitiesFor(ModelProvider.GOOGLE, "gemini-pro"))
                .isEqualTo(ModelCapabilities.unknown());
    }
}
