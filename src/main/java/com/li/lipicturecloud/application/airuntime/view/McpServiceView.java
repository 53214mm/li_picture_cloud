package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.McpConnection;

/**
 * MCP 服务的安全展示视图（平台管理面）。
 */
public record McpServiceView(
        long id,
        String code,
        String displayName,
        String endpointUri,
        boolean enabled,
        long revision) {

    public static McpServiceView of(McpConnection connection) {
        return new McpServiceView(connection.id(), connection.code(), connection.displayName(),
                connection.endpointUri().toString(), connection.enabled(), connection.revision());
    }
}
