package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 白名单裁决必须是 fail-closed：服务缺失/停用、工具未入白名单或停用，一律拒绝。
 */
class DbMcpToolAccessDeciderTest {

    private McpConnectionRepository connectionRepository;
    private McpToolWhitelistRepository whitelistRepository;
    private DbMcpToolAccessDecider decider;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(McpConnectionRepository.class);
        whitelistRepository = mock(McpToolWhitelistRepository.class);
        decider = new DbMcpToolAccessDecider(connectionRepository, whitelistRepository);
    }

    private McpConnection connection(boolean enabled) {
        return McpConnection.restore(4L, "mxai-mcp-server", "MxAI",
                URI.create("https://mcp.mxai.cn"), enabled, 1L);
    }

    @Test
    void allowsOnlyEnabledServicesAndEnabledWhitelistEntries() {
        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection(true)));
        when(whitelistRepository.findByConnectionAndTool(4L, "generate_image"))
                .thenReturn(Optional.of(McpToolWhitelist.restore(7L, 4L, "generate_image",
                        true, 0L)));

        assertThat(decider.isToolAllowed("mxai-mcp-server", "generate_image")).isTrue();
    }

    @Test
    void failsClosedOnEveryGap() {
        when(connectionRepository.findByCode("ghost")).thenReturn(Optional.empty());
        assertThat(decider.isToolAllowed("ghost", "tool")).isFalse();

        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection(false)));
        assertThat(decider.isToolAllowed("mxai-mcp-server", "tool")).isFalse();

        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection(true)));
        when(whitelistRepository.findByConnectionAndTool(4L, "unlisted"))
                .thenReturn(Optional.empty());
        assertThat(decider.isToolAllowed("mxai-mcp-server", "unlisted")).isFalse();

        when(whitelistRepository.findByConnectionAndTool(4L, "paused"))
                .thenReturn(Optional.of(McpToolWhitelist.restore(7L, 4L, "paused", false, 1L)));
        assertThat(decider.isToolAllowed("mxai-mcp-server", "paused")).isFalse();
    }
}
