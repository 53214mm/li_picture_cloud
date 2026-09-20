package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelConnectionTest {

    private static final URI HTTPS = URI.create("https://api.example.com/v1");

    @Test
    void createYieldsDisabledConnectionAtRevisionZero() {
        ModelConnection connection = ModelConnection.create(7L, ModelProvider.DEEPSEEK,
                "DeepSeek 主力", HTTPS, "deepseek-chat", null);

        assertThat(connection.id()).isNull();
        assertThat(connection.subjectId()).isEqualTo(7L);
        assertThat(connection.provider()).isEqualTo(ModelProvider.DEEPSEEK);
        assertThat(connection.displayName()).isEqualTo("DeepSeek 主力");
        assertThat(connection.endpointUri()).isEqualTo(HTTPS);
        assertThat(connection.modelCode()).isEqualTo("deepseek-chat");
        assertThat(connection.credentialId()).isNull();
        assertThat(connection.enabled()).isFalse();
        assertThat(connection.revision()).isZero();
    }

    @Test
    void createStripsDisplayNameWhitespace() {
        ModelConnection connection = ModelConnection.create(7L, ModelProvider.DEEPSEEK,
                "  DeepSeek 主力  ", HTTPS, "deepseek-chat", null);
        assertThat(connection.displayName()).isEqualTo("DeepSeek 主力");
    }

    @Test
    void rejectsInvalidIdentitiesAndRevisions() {
        assertThatThrownBy(() -> ModelConnection.create(0L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.restore(0L, 7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null, false, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.restore(-1L, 7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null, false, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.restore(null, 7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null, false, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.restore(1L, 7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null, false, -1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullProviderAndBadDisplayNames() {
        assertThatThrownBy(() -> ModelConnection.create(7L, null, "主力",
                HTTPS, "deepseek-chat", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, null,
                HTTPS, "deepseek-chat", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "",
                HTTPS, "deepseek-chat", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "bad;name",
                HTTPS, "deepseek-chat", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "x".repeat(65),
                HTTPS, "deepseek-chat", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpsEndpointsAndBadModelCodes() {
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                null, "deepseek-chat", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("http://api.example.com/v1"), "deepseek-chat", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("file:///etc/passwd"), "deepseek-chat", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek chat", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEndpointsWithQueryOrFragment() {
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.example.com/v1?tenant=a"), "deepseek-chat", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.example.com/v1#section"), "deepseek-chat", null))
                .isInstanceOf(IllegalArgumentException.class);
        // userinfo 混淆主机的端点一律拒绝。
        assertThatThrownBy(() -> ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://evil@api.example.com/v1"), "deepseek-chat", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        ModelConnection created = ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null);

        ModelConnection persisted = created.withId(11L);
        assertThat(persisted.id()).isEqualTo(11L);

        assertThatThrownBy(() -> persisted.withId(12L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enableAndDisableAdvanceRevisionByExactlyOne() {
        ModelConnection created = ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", null).withId(11L);

        ModelConnection enabled = created.enable();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.revision()).isEqualTo(1L);
        assertThat(enabled.enable()).isSameAs(enabled);

        ModelConnection disabled = enabled.disable();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.revision()).isEqualTo(2L);
        assertThat(disabled.disable()).isSameAs(disabled);
    }

    @Test
    void rotateCredentialAdvancesRevisionAndRejectsBadIds() {
        ModelConnection connection = ModelConnection.create(7L, ModelProvider.DEEPSEEK, "主力",
                HTTPS, "deepseek-chat", 21L).withId(11L);

        ModelConnection rotated = connection.rotateCredential(33L);
        assertThat(rotated.credentialId()).isEqualTo(33L);
        assertThat(rotated.revision()).isEqualTo(1L);

        assertThatThrownBy(() -> connection.rotateCredential(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
