package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRouteDecisionTest {

    private static final ModelConnection CONNECTION = ModelConnection.restore(9L, 7L,
            ModelProvider.DEEPSEEK, "主力", URI.create("https://api.deepseek.com/v1"),
            "deepseek-chat", 5L, true, 1L);

    @Test
    void platformRouteCarriesNoConnectionOrCredential() {
        ModelRouteDecision platform = ModelRouteDecision.platform();

        assertThat(platform.costSource()).isEqualTo(CostSource.PLATFORM);
        assertThat(platform.connection()).isNull();
        assertThat(platform.apiKey()).isNull();
        assertThat(platform.isByok()).isFalse();
    }

    @Test
    void byokRouteCarriesConnectionAndDecryptedKey() {
        ModelRouteDecision byok = ModelRouteDecision.byok(CONNECTION, "sk-secret");

        assertThat(byok.costSource()).isEqualTo(CostSource.BYOK);
        assertThat(byok.connection()).isEqualTo(CONNECTION);
        assertThat(byok.apiKey()).isEqualTo("sk-secret");
        assertThat(byok.isByok()).isTrue();
    }

    @Test
    void rejectsMixedFields() {
        assertThatThrownBy(() -> new ModelRouteDecision(CostSource.PLATFORM, CONNECTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelRouteDecision(CostSource.PLATFORM, null, "sk"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelRouteDecision(CostSource.BYOK, null, "sk"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelRouteDecision.byok(CONNECTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelRouteDecision.byok(CONNECTION, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        ModelConnection withoutCredential = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", null, true, 1L);
        assertThatThrownBy(() -> ModelRouteDecision.byok(withoutCredential, "sk"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
