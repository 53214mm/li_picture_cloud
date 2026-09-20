package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * MCP 工具白名单条目：只有被平台审核并显式列出的工具才会暴露给伙伴。
 * enabled=false 表示临时停用；不在白名单里的工具一律不开放（fail-closed）。
 */
public record McpToolWhitelist(
        Long id,
        long connectionId,
        String toolName,
        boolean enabled,
        long revision) {

    private static final Pattern TOOL = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");

    public McpToolWhitelist {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (connectionId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid whitelist identity or revision");
        }
        if (toolName == null || !TOOL.matcher(toolName).matches()) {
            throw new IllegalArgumentException("toolName must match " + TOOL.pattern());
        }
        Objects.requireNonNull(toolName, "toolName");
    }

    public static McpToolWhitelist create(long connectionId, String toolName) {
        return new McpToolWhitelist(null, connectionId, toolName, true, 0L);
    }

    public static McpToolWhitelist restore(Long id, long connectionId, String toolName,
                                           boolean enabled, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new McpToolWhitelist(id, connectionId, toolName, enabled, revision);
    }

    public McpToolWhitelist withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new McpToolWhitelist(persistedId, connectionId, toolName, enabled, revision);
    }

    public McpToolWhitelist enable() {
        return enabled ? this : new McpToolWhitelist(id, connectionId, toolName, true,
                Math.addExact(revision, 1L));
    }

    public McpToolWhitelist disable() {
        return enabled ? new McpToolWhitelist(id, connectionId, toolName, false,
                Math.addExact(revision, 1L)) : this;
    }
}
