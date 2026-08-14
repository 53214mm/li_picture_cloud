package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.airuntime.McpConnectionService;
import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MCP 管理面契约：请求校验与响应视图只含安全字段。
 */
class McpControllerTest {

    private MockMvc mockMvc;
    private McpConnectionService mcpConnectionService;

    @BeforeEach
    void setUp() {
        mcpConnectionService = mock(McpConnectionService.class);
        McpController controller = new McpController(mcpConnectionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upsertServiceValidatesAndReturnsSafeView() throws Exception {
        McpConnection created = McpConnection.restore(4L, "mxai-mcp-server", "MxAI",
                URI.create("https://mcp.mxai.cn"), false, 0L);
        when(mcpConnectionService.upsertService(eq("mxai-mcp-server"), eq("MxAI"),
                eq("https://mcp.mxai.cn"))).thenReturn(created);

        mockMvc.perform(post("/model/mcp/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"mxai-mcp-server","displayName":"MxAI",
                                 "endpointUri":"https://mcp.mxai.cn"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("mxai-mcp-server"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/model/mcp/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void toolWhitelistEndpointsFlowThrough() throws Exception {
        McpToolWhitelist entry = McpToolWhitelist.restore(7L, 4L, "generate_image", true, 0L);
        when(mcpConnectionService.addTool("mxai-mcp-server", "generate_image"))
                .thenReturn(entry);
        when(mcpConnectionService.listTools("mxai-mcp-server")).thenReturn(List.of(entry));
        when(mcpConnectionService.disableTool("mxai-mcp-server", "generate_image"))
                .thenReturn(entry.disable());
        when(mcpConnectionService.removeTool("mxai-mcp-server", "generate_image")).thenReturn(true);

        mockMvc.perform(post("/model/mcp/services/mxai-mcp-server/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolName\":\"generate_image\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("generate_image"));

        mockMvc.perform(get("/model/mcp/services/mxai-mcp-server/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].enabled").value(true));

        mockMvc.perform(post("/model/mcp/services/mxai-mcp-server/tools/generate_image/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete("/model/mcp/services/mxai-mcp-server/tools/generate_image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        verify(mcpConnectionService).addTool(eq("mxai-mcp-server"), eq("generate_image"));
    }
}
