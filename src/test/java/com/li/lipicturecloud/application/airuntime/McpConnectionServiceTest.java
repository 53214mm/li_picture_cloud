package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelistRepository;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpConnectionServiceTest {

    private McpConnectionRepository connectionRepository;
    private McpToolWhitelistRepository whitelistRepository;
    private McpToolCacheInvalidator cacheInvalidator;
    @SuppressWarnings("unchecked")
    private ObjectProvider<McpToolCacheInvalidator> cacheProvider = mock(ObjectProvider.class);
    private McpConnectionService service;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(McpConnectionRepository.class);
        whitelistRepository = mock(McpToolWhitelistRepository.class);
        cacheInvalidator = mock(McpToolCacheInvalidator.class);
        when(cacheProvider.getIfAvailable()).thenReturn(cacheInvalidator);
        service = new McpConnectionService(connectionRepository, whitelistRepository, cacheProvider);
    }

    private McpConnection connection() {
        return McpConnection.restore(4L, "mxai-mcp-server", "MxAI",
                URI.create("https://mcp.mxai.cn"), true, 1L);
    }

    @Test
    void upsertServiceReusesExistingRowAndInvalidatesCache() {
        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection()));

        assertThat(service.upsertService("mxai-mcp-server", "MxAI",
                "https://mcp.mxai.cn")).isEqualTo(connection());
        verify(cacheInvalidator).invalidateToolCache();
    }

    @Test
    void enableDisableServiceAdvancesThroughCas() {
        McpConnection enabled = connection();
        when(connectionRepository.findByCode("mxai-mcp-server")).thenReturn(Optional.of(enabled));
        when(connectionRepository.save(any(McpConnection.class), eq(1L))).thenReturn(true);

        McpConnection disabled = service.disableService("mxai-mcp-server");
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.revision()).isEqualTo(2L);

        when(connectionRepository.findByCode("mxai-mcp-server")).thenReturn(Optional.of(disabled));
        when(connectionRepository.save(any(McpConnection.class), eq(2L))).thenReturn(true);
        assertThat(service.enableService("mxai-mcp-server").enabled()).isTrue();
        verify(cacheInvalidator, org.mockito.Mockito.times(2)).invalidateToolCache();
    }

    @Test
    void addAndToggleAndRemoveToolsAllInvalidateCache() {
        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection()));
        when(whitelistRepository.findByConnectionAndTool(4L, "generate_image"))
                .thenReturn(Optional.empty());
        when(whitelistRepository.insert(any(McpToolWhitelist.class))).thenAnswer(invocation ->
                invocation.<McpToolWhitelist>getArgument(0).withId(7L));

        McpToolWhitelist added = service.addTool("mxai-mcp-server", "generate_image");
        assertThat(added.id()).isEqualTo(7L);
        assertThat(added.enabled()).isTrue();

        when(whitelistRepository.findByConnectionAndTool(4L, "generate_image"))
                .thenReturn(Optional.of(added));
        when(whitelistRepository.save(any(McpToolWhitelist.class), eq(0L))).thenReturn(true);
        McpToolWhitelist disabled = service.disableTool("mxai-mcp-server", "generate_image");
        assertThat(disabled.enabled()).isFalse();

        when(whitelistRepository.findByConnectionAndTool(4L, "generate_image"))
                .thenReturn(Optional.of(disabled));
        when(whitelistRepository.delete(7L, 1L)).thenReturn(true);
        assertThat(service.removeTool("mxai-mcp-server", "generate_image")).isTrue();
        verify(cacheInvalidator, org.mockito.Mockito.times(3)).invalidateToolCache();
    }

    @Test
    void missingServicesAndToolsSurfaceAsNotFound() {
        when(connectionRepository.findByCode("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listTools("ghost"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP 服务不存在");

        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection()));
        when(whitelistRepository.findByConnectionAndTool(4L, "ghost_tool"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.disableTool("mxai-mcp-server", "ghost_tool"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("白名单工具不存在");
    }

    @Test
    void listToolsReturnsAllEntriesForService() {
        when(connectionRepository.findByCode("mxai-mcp-server"))
                .thenReturn(Optional.of(connection()));
        McpToolWhitelist entry = McpToolWhitelist.restore(7L, 4L, "generate_image", true, 0L);
        when(whitelistRepository.findByConnectionId(4L)).thenReturn(List.of(entry));

        assertThat(service.listTools("mxai-mcp-server")).containsExactly(entry);
    }
}
