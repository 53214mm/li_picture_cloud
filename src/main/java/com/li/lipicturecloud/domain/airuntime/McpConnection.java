package com.li.lipicturecloud.domain.airuntime;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台审核通过的 MCP 服务连接。code 是平台维护的服务代码（与配置中的连接名对应），
 * 端点由平台设置，任意 URL 仍不开放。
 */
public record McpConnection(
        Long id,
        String code,
        String displayName,
        URI endpointUri,
        boolean enabled,
        long revision) {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9\\-]{0,63}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _\\-]{1,64}");

    public McpConnection {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code must match " + CODE.pattern());
        }
        if (displayName == null || !NAME.matcher(displayName.strip()).matches()) {
            throw new IllegalArgumentException("displayName must be 1-64 safe characters");
        }
        displayName = displayName.strip();
        Objects.requireNonNull(endpointUri, "endpointUri");
        if (!"https".equalsIgnoreCase(endpointUri.getScheme()) || endpointUri.getHost() == null
                || endpointUri.getRawUserInfo() != null || endpointUri.getRawQuery() != null
                || endpointUri.getRawFragment() != null) {
            throw new IllegalArgumentException("endpointUri must be a plain https URL");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    public static McpConnection create(String code, String displayName, URI endpointUri) {
        return new McpConnection(null, code, displayName, endpointUri, false, 0L);
    }

    public static McpConnection restore(Long id, String code, String displayName,
                                        URI endpointUri, boolean enabled, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new McpConnection(id, code, displayName, endpointUri, enabled, revision);
    }

    public McpConnection withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new McpConnection(persistedId, code, displayName, endpointUri, enabled, revision);
    }

    public McpConnection enable() {
        return enabled ? this : new McpConnection(id, code, displayName, endpointUri, true,
                Math.addExact(revision, 1L));
    }

    public McpConnection disable() {
        return enabled ? new McpConnection(id, code, displayName, endpointUri, false,
                Math.addExact(revision, 1L)) : this;
    }
}
