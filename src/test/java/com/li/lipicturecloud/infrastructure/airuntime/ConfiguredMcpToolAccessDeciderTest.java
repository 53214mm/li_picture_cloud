package com.li.lipicturecloud.infrastructure.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredMcpToolAccessDeciderTest {

    @Test
    void nothingIsAllowedUntilToolsAreConfigured() {
        ConfiguredMcpToolAccessDecider decider = new ConfiguredMcpToolAccessDecider("mxai-mcp-server", "");

        assertThat(decider.isToolAllowed("mxai-mcp-server", "generate_image")).isFalse();
    }

    @Test
    void onlyListedToolsOfTheConfiguredServiceAreAllowed() {
        ConfiguredMcpToolAccessDecider decider = new ConfiguredMcpToolAccessDecider("mxai-mcp-server",
                " generate_image , get_task_status ");

        assertThat(decider.isToolAllowed("mxai-mcp-server", "generate_image")).isTrue();
        assertThat(decider.isToolAllowed("mxai-mcp-server", "get_task_status")).isTrue();
        assertThat(decider.isToolAllowed("mxai-mcp-server", "generate_video")).isFalse();
        assertThat(decider.isToolAllowed("other-server", "generate_image")).isFalse();
        assertThat(decider.isToolAllowed("mxai-mcp-server", null)).isFalse();
    }
}
