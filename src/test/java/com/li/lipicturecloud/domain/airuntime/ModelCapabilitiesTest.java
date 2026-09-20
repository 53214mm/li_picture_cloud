package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCapabilitiesTest {

    @Test
    void unknownMeansNothingIsSupported() {
        ModelCapabilities unknown = ModelCapabilities.unknown();

        assertThat(unknown.text()).isFalse();
        assertThat(unknown.vision()).isFalse();
        assertThat(unknown.toolCall()).isFalse();
        assertThat(unknown.structuredOutput()).isFalse();
        assertThat(unknown.reasoning()).isFalse();
        assertThat(unknown.embedding()).isFalse();
        assertThat(unknown.imageGeneration()).isFalse();
        assertThat(unknown.maxContextTokens()).isNull();
        assertThat(unknown.syncAsync()).isEqualTo("UNKNOWN");
        assertThat(unknown.costHint()).isNull();
    }

    @Test
    void ofBuildsExplicitCapabilities() {
        ModelCapabilities capabilities = ModelCapabilities.of(true, true, false, true, false,
                false, false, 32_000, ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP);

        assertThat(capabilities.text()).isTrue();
        assertThat(capabilities.vision()).isTrue();
        assertThat(capabilities.maxContextTokens()).isEqualTo(32_000);
        assertThat(capabilities.syncAsync()).isEqualTo("SYNC");
        assertThat(capabilities.costHint()).isEqualTo("CHEAP");
    }

    @Test
    void rejectsInvalidTokensModesAndCostHints() {
        assertThatThrownBy(() -> new ModelCapabilities(false, false, false, false, false, false,
                false, 0, "UNKNOWN", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCapabilities(false, false, false, false, false, false,
                false, null, "STREAMING", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCapabilities(false, false, false, false, false, false,
                false, null, "UNKNOWN", "FREE")).isInstanceOf(IllegalArgumentException.class);
        // null 模式被模式校验先拦截（等价 IllegalArgumentException，而非 NPE）。
        assertThatThrownBy(() -> new ModelCapabilities(false, false, false, false, false, false,
                false, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
