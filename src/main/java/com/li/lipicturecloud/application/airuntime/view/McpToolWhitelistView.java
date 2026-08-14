package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;

/**
 * MCP 工具白名单条目的展示视图（平台管理面）。
 */
public record McpToolWhitelistView(
        long id,
        String toolName,
        boolean enabled,
        long revision) {

    public static McpToolWhitelistView of(McpToolWhitelist entry) {
        return new McpToolWhitelistView(entry.id(), entry.toolName(), entry.enabled(),
                entry.revision());
    }
}
