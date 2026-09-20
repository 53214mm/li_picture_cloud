package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * MCP 工具白名单的持久化端口。(connectionId, toolName) 唯一。
 */
public interface McpToolWhitelistRepository {

    List<McpToolWhitelist> findByConnectionId(long connectionId);

    Optional<McpToolWhitelist> findByConnectionAndTool(long connectionId, String toolName);

    McpToolWhitelist insert(McpToolWhitelist entry);

    boolean save(McpToolWhitelist after, long expectedRevision);

    boolean delete(long id, long expectedRevision);
}
