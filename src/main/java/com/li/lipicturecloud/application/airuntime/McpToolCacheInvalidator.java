package com.li.lipicturecloud.application.airuntime;

/**
 * MCP 工具缓存失效端口：白名单/启停变更后让工具提供者丢弃缓存，立即重新裁决。
 */
public interface McpToolCacheInvalidator {

    void invalidateToolCache();
}
