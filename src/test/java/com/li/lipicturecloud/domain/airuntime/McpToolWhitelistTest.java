package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolWhitelistTest {

    @Test
    void createYieldsEnabledEntryAtRevisionZero() {
        McpToolWhitelist entry = McpToolWhitelist.create(4L, "generate_image");

        assertThat(entry.id()).isNull();
        assertThat(entry.connectionId()).isEqualTo(4L);
        assertThat(entry.toolName()).isEqualTo("generate_image");
        assertThat(entry.enabled()).isTrue();
        assertThat(entry.revision()).isZero();
    }

    @Test
    void rejectsBadIdentitiesAndToolNames() {
        assertThatThrownBy(() -> McpToolWhitelist.create(0L, "tool"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolWhitelist.create(4L, "bad tool"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolWhitelist.create(4L, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolWhitelist.restore(null, 4L, "tool", true, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolWhitelist.restore(1L, 4L, "tool", true, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enableDisableAndWithIdAdvanceRevisionExactlyOnce() {
        McpToolWhitelist created = McpToolWhitelist.create(4L, "generate_image").withId(7L);

        McpToolWhitelist disabled = created.disable();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.revision()).isEqualTo(1L);
        assertThat(disabled.disable()).isSameAs(disabled);

        McpToolWhitelist enabled = disabled.enable();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.revision()).isEqualTo(2L);

        assertThatThrownBy(() -> enabled.withId(9L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void overflowAndRestoreGuards() {
        McpToolWhitelist max = McpToolWhitelist.restore(7L, 4L, "tool", true, Long.MAX_VALUE);
        assertThatThrownBy(() -> max.disable()).isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() -> McpToolWhitelist.restore(0L, 4L, "tool", true, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolWhitelist.restore(7L, 0L, "tool", true, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
