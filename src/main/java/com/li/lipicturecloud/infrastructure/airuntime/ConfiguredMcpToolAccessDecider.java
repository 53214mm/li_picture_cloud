package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.McpToolAccessDecider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按配置放行 MCP 工具：只有 {@code app.ai.mcp.service} 这个平台服务里、列在
 * {@code app.ai.mcp.allowed-tools} 中的工具才允许调用。没配置就一个都不放行（fail-closed）。
 */
@Component
public class ConfiguredMcpToolAccessDecider implements McpToolAccessDecider {

    private final String serviceCode;
    private final Set<String> allowedTools;

    public ConfiguredMcpToolAccessDecider(@Value("${app.ai.mcp.service:mxai-mcp-server}") String serviceCode,
                                          @Value("${app.ai.mcp.allowed-tools:}") String allowedTools) {
        this.serviceCode = Objects.requireNonNull(serviceCode, "serviceCode").strip();
        this.allowedTools = Arrays.stream(Objects.requireNonNullElse(allowedTools, "").split(","))
                .map(String::strip).filter(tool -> !tool.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isToolAllowed(String serviceCode, String toolName) {
        return this.serviceCode.equals(serviceCode) && toolName != null && allowedTools.contains(toolName);
    }
}
