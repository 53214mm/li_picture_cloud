package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpConnectionTest {

    private static final URI ENDPOINT = URI.create("https://mcp.mxai.cn");

    @Test
    void createYieldsDisabledConnectionAtRevisionZero() {
        McpConnection connection = McpConnection.create("mxai-mcp-server", "MxAI 服务",
                ENDPOINT);

        assertThat(connection.id()).isNull();
        assertThat(connection.code()).isEqualTo("mxai-mcp-server");
        assertThat(connection.enabled()).isFalse();
        assertThat(connection.revision()).isZero();
    }

    @Test
    void rejectsBadCodesNamesAndEndpoints() {
        assertThatThrownBy(() -> McpConnection.create("Bad_Code", "x", ENDPOINT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.create("ok-code", "bad;name", ENDPOINT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.create("ok-code", "x", URI.create("http://mcp.mxai.cn")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.create("ok-code", "x",
                URI.create("https://evil@mcp.mxai.cn"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.create("ok-code", "x",
                URI.create("https://mcp.mxai.cn/path?q=1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.restore(null, "ok-code", "x", ENDPOINT, false, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpConnection.restore(1L, "ok-code", "x", ENDPOINT, false, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enableDisableAndWithIdAdvanceRevisionExactlyOnce() {
        McpConnection created = McpConnection.create("mxai-mcp-server", "MxAI", ENDPOINT)
                .withId(4L);

        McpConnection enabled = created.enable();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.revision()).isEqualTo(1L);
        assertThat(enabled.enable()).isSameAs(enabled);

        McpConnection disabled = enabled.disable();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.revision()).isEqualTo(2L);
        assertThat(disabled.disable()).isSameAs(disabled);

        assertThatThrownBy(() -> disabled.withId(9L)).isInstanceOf(IllegalStateException.class);
    }
}
