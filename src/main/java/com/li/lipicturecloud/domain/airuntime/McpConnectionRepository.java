package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * MCP 服务连接的持久化端口。code 唯一。
 */
public interface McpConnectionRepository {

    Optional<McpConnection> findByCode(String code);

    List<McpConnection> findAll();

    McpConnection insert(McpConnection connection);

    boolean save(McpConnection after, long expectedRevision);

    boolean delete(long id, long expectedRevision);
}
